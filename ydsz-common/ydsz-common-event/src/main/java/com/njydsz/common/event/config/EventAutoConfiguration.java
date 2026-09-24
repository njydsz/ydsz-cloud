package com.njydsz.common.event.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import com.njydsz.common.event.admin.OutboxAdminController;
import com.njydsz.common.event.admin.OutboxAdminService;
import com.njydsz.common.event.archive.OutboxArchiveRepository;
import com.njydsz.common.event.archive.OutboxArchiveRepositoryJdbc;
import com.njydsz.common.event.consumer.OutboxIdempotentAspect;
import com.njydsz.common.event.consumer.OutboxSubscriber;
import com.njydsz.common.event.consumer.OutboxSubscriberDispatcher;
import com.njydsz.common.event.gateway.ChannelEventPublishGateway;
import com.njydsz.common.event.gateway.EventChannelDefinition;
import com.njydsz.common.event.gateway.EventChannelRegistry;
import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.gateway.KafkaEventPublishGateway;
import com.njydsz.common.event.gateway.NoopEventPublishGateway;
import com.njydsz.common.event.gateway.OutboxObservationGateway;
import com.njydsz.common.event.gateway.RocketMqEventPublishGateway;
import com.njydsz.common.event.health.OutboxHealthIndicator;
import com.njydsz.common.event.processor.OutboxProcessor;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.event.saga.SagaManager;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * Outbox 事件模块自动配置
 *
 * <p>当 {@code ydsz.event.outbox.enabled=true}（默认）且容器中存在 {@link JdbcTemplate} 时自动装配。
 *
 * <p>投递网关优先级：
 *
 * <ol>
 *   <li>容器中已有的 {@link EventPublishGateway} Bean（业务模块自定义）
 *   <li>当 RocketMQTemplate 在 classpath 时，通过 {@link RocketMqGatewayConfiguration} 自动注册
 *   <li>降级为 {@link NoopEventPublishGateway}（生产环境应设置 fail-on-noop=true 阻止启动）
 * </ol>
 *
 * <p><b>条件装配说明：</b>
 *
 * <ul>
 *   <li>{@link RocketMqGatewayConfiguration} 作为嵌套 {@code @Configuration} 类， 通过
 *       {@code @ConditionalOnClass} / {@code @ConditionalOnBean} 条件控制加载
 *   <li>当 RocketMQ 不在 classpath 时，整个嵌套配置类不加载，不会创建相关 Bean
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 将 {@code @Import(RocketMqGatewayConfiguration.class)} 改为嵌套 {@code @Configuration}，
 *     修复条件注解失效问题
 * @since 26.09.01 移除 JSON Schema 校验框架和同步投递模式的自动配置，精简职责
 * @since 26.09.01 移除已废弃的 EventStore 接口支持，统一使用 DomainEventPublisher
 */
@AutoConfiguration
@EnableConfigurationProperties(EventProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.event.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnBean(JdbcTemplate.class)
public class EventAutoConfiguration {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(EventAutoConfiguration.class);

  /** 当前激活的 Outbox 后台处理器，销毁时调用 stop() 停机。 */
  private OutboxProcessor outboxProcessor;

  /** 当前激活的事件投递网关，启动后校验是否为 Noop。 */
  private EventPublishGateway activeGateway;

  /** 当前激活的事件配置属性，启动后校验网关合规性。 */
  private EventProperties activeProperties;

  /**
   * 创建 Outbox 仓储实例
   *
   * @param jdbcTemplate JDBC 模板
   * @param properties 事件配置属性
   * @return Outbox 仓储实例
   */
  @Bean
  @ConditionalOnMissingBean
  public OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate, EventProperties properties) {
    OutboxRepository repository = new OutboxRepository(jdbcTemplate, properties.getTableName());
    // 设置 countByStatus 缓存 TTL
    repository.setCacheTtlMillis(properties.getStatusCountCacheSeconds() * 1000L);
    LOG.info(
        "Outbox repository initialized: table={}, cacheTtl={}s",
        properties.getTableName(),
        properties.getStatusCountCacheSeconds());
    return repository;
  }

  /**
   * 创建 Outbox 写入服务
   *
   * @param outboxRepository Outbox 仓储
   * @param properties 事件配置属性
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @param eventPublisher Spring 事件发布器
   * @return Outbox 写入服务实例
   */
  @Bean
  @ConditionalOnMissingBean
  public OutboxService outboxService(
      OutboxRepository outboxRepository,
      EventProperties properties,
      SnowflakeIdGenerator snowflakeIdGenerator,
      ApplicationEventPublisher eventPublisher) {
    return new OutboxService(outboxRepository, properties, snowflakeIdGenerator, eventPublisher);
  }

  /**
   * 创建 Outbox 订阅者分发器（YDIZ-EVENT-002）。
   *
   * <p>自动收集容器中所有 {@link OutboxSubscriber} 实现 Bean，按 topic 索引后接收
   * OutboxMessage 事件并路由到匹配的订阅者。
   *
   * <p>当容器中不存在任何 OutboxSubscriber 实现 Bean 时，不注册分发器（避免无意义的事件监听开销）。
   *
   * @param subscribers 容器中所有 OutboxSubscriber 实现 Bean（可为空列表）
   * @return 分发器实例；若无可注册订阅者则返回 null（不注册 Bean）
   */
  @Bean
  @ConditionalOnMissingBean
  public OutboxSubscriberDispatcher outboxSubscriberDispatcher(
      List<OutboxSubscriber> subscribers) {
    if (subscribers == null || subscribers.isEmpty()) {
      LOG.info("容器中无 OutboxSubscriber 实现，跳过分发器注册");
      return null;
    }
    return new OutboxSubscriberDispatcher(subscribers);
  }

  /**
   * 创建事件投递网关降级实现
   *
   * <p>当容器中不存在其他 EventPublishGateway 实现且 RocketMQTemplate 不可用时使用 Noop 实现。
   *
   * @param properties 事件配置属性
   * @return Noop 事件投递网关实例
   */
  @Bean
  @ConditionalOnMissingBean(EventPublishGateway.class)
  public EventPublishGateway noopEventPublishGateway(EventProperties properties) {
    LOG.warn(
        "No EventPublishGateway found, using NoopEventPublishGateway. "
            + "Messages will not be actually published to any message queue.");
    return new NoopEventPublishGateway();
  }

  /**
   * 创建 Outbox 后台处理器
   *
   * @param outboxRepository Outbox 仓储
   * @param publishGateway 投递网关
   * @param properties 事件配置属性
   * @param meterRegistryProvider Micrometer 指标注册器提供者（可选）
   * @return Outbox 后台处理器实例
   */
  @Bean(initMethod = "start")
  public OutboxProcessor outboxProcessor(
      OutboxRepository outboxRepository,
      EventPublishGateway publishGateway,
      EventProperties properties,
      ObjectProvider<MeterRegistry> meterRegistryProvider) {
    this.activeGateway = publishGateway;
    this.activeProperties = properties;
    OutboxProcessor processor =
        new OutboxProcessor(
            outboxRepository, publishGateway, properties, meterRegistryProvider.getIfAvailable());
    this.outboxProcessor = processor;
    return processor;
  }

  /**
   * 创建 Outbox 健康检查指标
   *
   * <p>注入 {@link EventProperties} 以支持阈值可配（E-3）。
   *
   * @param outboxRepository Outbox 仓储
   * @param properties 事件配置属性
   * @return Outbox 健康指标实例
   */
  @Bean
  @ConditionalOnMissingBean
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  public OutboxHealthIndicator outboxHealthIndicator(
      OutboxRepository outboxRepository, EventProperties properties) {
    return new OutboxHealthIndicator(outboxRepository, properties);
  }

  /**
   * 创建 Outbox 运维管理服务
   *
   * @param outboxRepository Outbox 仓储
   * @return Outbox 运维管理服务实例
   */
  @Bean
  @ConditionalOnMissingBean
  public OutboxAdminService outboxAdminService(OutboxRepository outboxRepository) {
    return new OutboxAdminService(outboxRepository);
  }

  /**
   * 创建 Outbox 运维管理控制器（F-2）
   *
   * <p>不依赖 spring-web，业务 web 模块可通过继承或委托本 Controller 快速暴露 REST 接口。 已注册为
   * Bean，可被业务模块通过 {@code @Autowired} 注入使用。
   *
   * @param outboxAdminService Outbox 运维管理服务
   * @return Outbox 运维管理控制器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public OutboxAdminController outboxAdminController(OutboxAdminService outboxAdminService) {
    return new OutboxAdminController(outboxAdminService);
  }

  /**
   * 创建事件通道注册表（O-3）
   *
   * <p>建立 eventType → channel 的默认通道映射（workflow/user/flow → flow-events， 其他 →
   * default-events），并提供标准声明式配置入口，业务模块可在启动时动态注册自定义通道。
   *
   * @return 事件通道注册表 Bean 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public EventChannelRegistry eventChannelRegistry() {
    EventChannelRegistry registry = new EventChannelRegistry();

    // 默认通道
    registry.setDefaultChannel(
        new EventChannelDefinition("default-events", "ydsz-outbox-events", 0, "json"));

    // 流程事件通道
    registry.register(
        new EventChannelDefinition("flow-events", "ydsz-flow-events", 0, "json"));
    registry.registerEventType("FLOW_INSTANCE_STARTED", "flow-events");
    registry.registerEventType("FLOW_INSTANCE_APPROVED", "flow-events");
    registry.registerEventType("FLOW_INSTANCE_REJECTED", "flow-events");
    registry.registerEventType("FLOW_INSTANCE_TERMINATED", "flow-events");
    registry.registerEventType("FLOW_TASK_COMPLETED", "flow-events");
    registry.registerEventType("FLOW_URGE_TRIGGERED", "flow-events");

    // 用户事件通道
    registry.register(
        new EventChannelDefinition("user-events", "ydsz-user-events", 0, "json"));
    registry.registerEventType("USER_CREATED", "user-events");
    registry.registerEventType("USER_UPDATED", "user-events");
    registry.registerEventType("USER_DELETED", "user-events");
    registry.registerEventType("USER_ENABLED", "user-events");
    registry.registerEventType("USER_DISABLED", "user-events");
    registry.registerEventType("USER_LOGIN", "user-events");
    registry.registerEventType("USER_ROLE_CHANGED", "user-events");

    return registry;
  }

  /**
   * 创建 Outbox 归档仓储（F-4）
   *
   * <p>仅在归档功能启用时注册（{@code ydsz.event.outbox.archive.enabled=true}）。
   *
   * @param jdbcTemplate JDBC 模板
   * @param properties 事件配置属性
   * @return Outbox 归档仓储 Bean 实例（未启用时返回 null）
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.event.outbox.archive",
      name = "enabled",
      havingValue = "true")
  public OutboxArchiveRepository outboxArchiveRepository(JdbcTemplate jdbcTemplate,
      EventProperties properties) {
    return new OutboxArchiveRepositoryJdbc(jdbcTemplate, properties.getArchive().getTableName());
  }

  /**
   * 创建 Saga 编排管理器（F-1）
   *
   * <p>负责管理 AbstractSaga 实例的生命周期：路由事件、创建 Saga、清理已结束 Saga。 业务模块通过 {@code
   * SagaManager.registerSagaFactory()} 注册自定义 Saga。
   *
   * @return Saga 编排管理器实例
   */
  @Bean
  @ConditionalOnMissingBean
  public SagaManager sagaManager() {
    SagaManager manager = new SagaManager();
    LOG.info("SagaManager initialized. Register saga factories using SagaManager.registerSagaFactory()");
    return manager;
  }

  /**
   * 创建 Observation 投递网关装饰器（O-1）
   *
   * <p>当 micrometer-observation 在 classpath 时注册，包装底层网关以产出 Trace + Metrics。
   *
   * @param properties 事件配置属性
   * @param observationRegistryProvider ObservationRegistry 提供者（可选）
   * @return Observation 包装网关实例
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "io.micrometer.observation.ObservationRegistry")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  @ConditionalOnProperty(
      prefix = "ydsz.event.outbox.observation",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = false)
  public OutboxObservationGateway outboxObservationGateway(EventProperties properties,
      ObjectProvider<Object> observationRegistryProvider) {
    LOG.info("OutboxObservationGateway enabled (micrometer-observation on classpath)");
    return new OutboxObservationGateway(new NoopEventPublishGateway(), observationRegistryProvider);
  }

  /**
   * 创建幂等消费 AOP 切面（F-3）
   *
   * <p>当 spring-boot-starter-aop 在 classpath 时注册切面 Bean，拦截 {@link
   * com.njydsz.common.event.consumer.OutboxIdempotentConsumer} 注解的方法，实现消费去重。
   *
   * @param stringRedisTemplateProvider Redis 模板提供者（可选，不可用时降级为 JVM 本地缓存）
   * @return 幂等消费切面 Bean 实例
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
  // CHECKSTYLE.ON: RegexpSinglelineJava
  public OutboxIdempotentAspect outboxIdempotentAspect(
      ObjectProvider<Object> stringRedisTemplateProvider) {
    LOG.info("OutboxIdempotentAspect registered (spring-aop on classpath)");
    return new OutboxIdempotentAspect(stringRedisTemplateProvider);
  }

  /**
   * 启动后校验投递网关
   *
   * <p>如果使用 NoopEventPublishGateway 且 fail-on-noop=true，抛出异常阻止应用启动， 避免生产环境消息丢失。
   */
  @PostConstruct
  public void validateGateway() {
    if (activeProperties != null
        && activeProperties.isFailOnNoop()
        && activeGateway instanceof NoopEventPublishGateway) {
      throw new IllegalStateException(
          "NoopEventPublishGateway is in use and ydsz.event.outbox.fail-on-noop=true. "
              + "Please provide an EventPublishGateway implementation (e.g. RocketMQ) "
              + "or set ydsz.event.outbox.fail-on-noop=false to suppress this check.");
    }
  }

  /**
   * 容器销毁时优雅停机。
   *
   * <p>停止 {@link OutboxProcessor} 后台线程，确保未完成投递的事件被持久化且线程池被释放。
   */
  @PreDestroy
  public void destroy() {
    if (outboxProcessor != null) {
      outboxProcessor.stop();
    }
  }

  // ==================== 嵌套配置：RocketMQ 网关 ====================

    /**
     * RocketMQ 网关配置（嵌套配置类）
     *
     * <p>通过嵌套 {@code @Configuration} 类实现条件装配—— {@code @ConditionalOnClass} /
     * {@code @ConditionalOnBean} 会正常生效， 避免 {@code @Import} 导致的条件注解失效问题。
     *
     * <p>封装 RocketMQ 生产者 Bean 注册逻辑，支持事务消息、顺序消息、延迟消息。
     *
     * @author ydsz-team
     * @since 26.09.01
     * @since 26.09.01 由独立顶层配置类改为嵌套配置类
     */
    @Configuration
    // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
    @ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
    // CHECKSTYLE.ON: RegexpSinglelineJava
    @ConditionalOnBean(type = "org.apache.rocketmq.spring.core.RocketMQTemplate")
    public static class RocketMqGatewayConfiguration {

      /** 日志实例 */
      private static final Logger LOG = LoggerFactory.getLogger(RocketMqGatewayConfiguration.class);

      /**
       * 注册 RocketMQ 事件投递网关
       *
       * @param rocketMQTemplate RocketMQ 模板（由 rocketmq-spring-boot-starter 自动注册）
       * @return RocketMQ 网关实例
       */
      @Bean
      @ConditionalOnMissingBean(EventPublishGateway.class)
      public EventPublishGateway rocketMqEventPublishGateway(
          RocketMQTemplate rocketMQTemplate) {
        LOG.info("RocketMqEventPublishGateway registered: topic=ydsz-outbox-events");
        return new RocketMqEventPublishGateway(rocketMQTemplate, null);
      }
    }

    // ==================== 嵌套配置：Kafka 网关 ====================

    /**
     * Kafka 网关配置（嵌套配置类）。
     *
     * <p>通过嵌套 {@code @Configuration} 类实现条件装配——当 classpath 存在 Spring Kafka 且容器中存在
     * KafkaTemplate Bean 时自动注册 KafkaEventPublishGateway。
     *
     * <p>与 RocketMqGatewayConfiguration 互斥：Spring 容器中优先使用已存在的 EventPublishGateway Bean，
     * 当 KafkaTemplate 和 RocketMQTemplate 同时存在时，取决于 Bean 注册顺序（建议使用时仅引入一种 MQ 客户端）。
     *
     * @author ydsz-team
     * @since 26.09.13
     */
    @Configuration
    // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
    @ConditionalOnClass(name = "org.springframework.kafka.core.KafkaTemplate")
    // CHECKSTYLE.ON: RegexpSinglelineJava
    @ConditionalOnBean(KafkaTemplate.class)
    public static class KafkaGatewayConfiguration {

      /** 日志实例 */
      private static final Logger LOG = LoggerFactory.getLogger(KafkaGatewayConfiguration.class);

      /**
       * 注册 Kafka 事件投递网关
       *
       * @param kafkaTemplate Kafka 模板（由 spring-kafka 自动注册）
       * @return Kafka 网关实例
       */
      @Bean
      @ConditionalOnMissingBean(EventPublishGateway.class)
      public EventPublishGateway kafkaEventPublishGateway(
          KafkaTemplate<String, String> kafkaTemplate) {
        LOG.info("KafkaEventPublishGateway registered: topic=ydsz-outbox-events");
        return new KafkaEventPublishGateway(kafkaTemplate, null);
      }
    }
  }
