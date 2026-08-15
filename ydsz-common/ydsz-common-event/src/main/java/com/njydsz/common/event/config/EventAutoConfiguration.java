package com.njydsz.common.event.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.njydsz.common.event.admin.OutboxAdminService;
import com.njydsz.common.event.api.EventStore;
import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.gateway.NoopEventPublishGateway;
import com.njydsz.common.event.gateway.RocketMqEventPublishGateway;
import com.njydsz.common.event.health.OutboxHealthIndicator;
import com.njydsz.common.event.processor.OutboxProcessor;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.event.service.OutboxEventStore;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Outbox 事件模块自动配置
 *
 * <p>当 {@code ydsz.event.outbox.enabled=true}（默认）且容器中存在
 * {@link JdbcTemplate} 时自动装配。
 *
 * <p>投递网关优先级：
 * <ol>
 *   <li>容器中已有的 {@link EventPublishGateway} Bean（业务模块自定义）</li>
 *   <li>当 RocketMQTemplate 在 classpath 时，通过 {@link RocketMqGatewayConfiguration} 自动注册</li>
 *   <li>降级为 {@link NoopEventPublishGateway}（生产环境应设置 fail-on-noop=true 阻止启动）</li>
 * </ol>
 *
 * <p><b>条件装配说明：</b>
 * <ul>
 *   <li>{@link RocketMqGatewayConfiguration} 作为嵌套 {@code @Configuration} 类，
 *       通过 {@code @ConditionalOnClass} / {@code @ConditionalOnBean} 条件控制加载</li>
 *   <li>当 RocketMQ 不在 classpath 时，整个嵌套配置类不加载，不会创建相关 Bean</li>
 *   <li>当用户自定义 {@link EventStore} Bean 时，本类整体不加载，避免半成品状态</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.6.0 将 {@code @Import(RocketMqGatewayConfiguration.class)} 改为嵌套 {@code @Configuration}，
 *             修复条件注解失效问题；新增类级 {@code @ConditionalOnMissingBean(EventStore.class)} 守卫
 * @since 1.7.0 移除 JSON Schema 校验框架和同步投递模式的自动配置，精简职责
 */
@AutoConfiguration
@EnableConfigurationProperties(EventProperties.class)
@ConditionalOnProperty(prefix = "ydsz.event.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(JdbcTemplate.class)
@ConditionalOnMissingBean(EventStore.class)
public class EventAutoConfiguration {

    /** 日志实例 */
    private static final Logger log = LoggerFactory.getLogger(EventAutoConfiguration.class);

    private OutboxProcessor outboxProcessor;
    private EventPublishGateway activeGateway;
    private EventProperties activeProperties;

    /**
     * 创建 Outbox 仓储实例
     *
     * @param jdbcTemplate JDBC 模板
     * @param properties   事件配置属性
     * @return Outbox 仓储实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxRepository outboxRepository(JdbcTemplate jdbcTemplate,
                                              EventProperties properties) {
        OutboxRepository repository = new OutboxRepository(jdbcTemplate, properties.getTableName());
        // 设置 countByStatus 缓存 TTL
        repository.setCacheTtlMillis(properties.getStatusCountCacheSeconds() * 1000L);
        log.info("Outbox repository initialized: table={}, cacheTtl={}s",
                properties.getTableName(), properties.getStatusCountCacheSeconds());
        return repository;
    }

    /**
     * 创建 Outbox 写入服务
     *
     * @param outboxRepository     Outbox 仓储
     * @param properties           事件配置属性
     * @param snowflakeIdGenerator 分布式 ID 生成器
     * @param eventPublisher       Spring 事件发布器
     * @return Outbox 写入服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public OutboxService outboxService(OutboxRepository outboxRepository,
                                       EventProperties properties,
                                       SnowflakeIdGenerator snowflakeIdGenerator,
                                       org.springframework.context.ApplicationEventPublisher eventPublisher) {
        return new OutboxService(outboxRepository, properties, snowflakeIdGenerator, eventPublisher);
    }

    /**
     * 创建领域事件存储适配器（实现 common-domain 的 EventStore SPI）
     *
     * <p>当容器中不存在其他 EventStore 实现时，自动注册基于 Outbox 的适配器。
     *
     * @param outboxService Outbox 写入服务
     * @return Outbox 事件存储适配器实例
     */
    @Bean
    public OutboxEventStore outboxEventStore(OutboxService outboxService) {
        log.info("OutboxEventStore registered as default EventStore implementation");
        return new OutboxEventStore(outboxService);
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
        log.warn("No EventPublishGateway found, using NoopEventPublishGateway. "
                + "Messages will not be actually published to any message queue.");
        return new NoopEventPublishGateway();
    }

    /**
     * 创建 Outbox 后台处理器
     *
     * @param outboxRepository      Outbox 仓储
     * @param publishGateway        投递网关
     * @param properties            事件配置属性
     * @param meterRegistryProvider Micrometer 指标注册器提供者（可选）
     * @return Outbox 后台处理器实例
     */
    @Bean(initMethod = "start")
    public OutboxProcessor outboxProcessor(OutboxRepository outboxRepository,
                                           EventPublishGateway publishGateway,
                                           EventProperties properties,
                                           ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.activeGateway = publishGateway;
        this.activeProperties = properties;
        OutboxProcessor processor = new OutboxProcessor(
                outboxRepository,
                publishGateway,
                properties,
                meterRegistryProvider.getIfAvailable()
        );
        this.outboxProcessor = processor;
        return processor;
    }

    /**
     * 创建 Outbox 健康检查指标
     *
     * @param outboxRepository Outbox 仓储
     * @return Outbox 健康指标实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    public OutboxHealthIndicator outboxHealthIndicator(OutboxRepository outboxRepository) {
        return new OutboxHealthIndicator(outboxRepository);
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
     * 启动后校验投递网关
     *
     * <p>如果使用 NoopEventPublishGateway 且 fail-on-noop=true，抛出异常阻止应用启动，
     * 避免生产环境消息丢失。
     */
    @PostConstruct
    public void validateGateway() {
        if (activeProperties != null && activeProperties.isFailOnNoop()
                && activeGateway instanceof NoopEventPublishGateway) {
            throw new IllegalStateException(
                    "NoopEventPublishGateway is in use and ydsz.event.outbox.fail-on-noop=true. "
                            + "Please provide an EventPublishGateway implementation (e.g. RocketMQ) "
                            + "or set ydsz.event.outbox.fail-on-noop=false to suppress this check.");
        }
    }

    /**
     * 销毁时停止 Outbox 处理器
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
     * <p>通过嵌套 {@code @Configuration} 类实现条件装配——
     * {@code @ConditionalOnClass} / {@code @ConditionalOnBean} 会正常生效，
     * 避免 {@code @Import} 导致的条件注解失效问题。
     *
     * <p>封装 RocketMQ 生产者 Bean 注册逻辑，支持事务消息、顺序消息、延迟消息。
     *
     * @author ydsz-team
     * @since 1.0.0
     * @since 1.6.0 由独立顶层配置类改为嵌套配置类
     */
    @Configuration
    @ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
    @ConditionalOnBean(type = "org.apache.rocketmq.spring.core.RocketMQTemplate")
    public static class RocketMqGatewayConfiguration {

        /** 日志实例 */
        private static final Logger log = LoggerFactory.getLogger(RocketMqGatewayConfiguration.class);

        /**
         * 注册 RocketMQ 事件投递网关
         *
         * @param rocketMQTemplate RocketMQ 模板（由 rocketmq-spring-boot-starter 自动注册）
         * @return RocketMQ 网关实例
         */
        @Bean
        @ConditionalOnMissingBean(EventPublishGateway.class)
        public EventPublishGateway rocketMqEventPublishGateway(
                org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate) {
            log.info("RocketMqEventPublishGateway registered: topic=ydsz-outbox-events");
            return new RocketMqEventPublishGateway(rocketMQTemplate, null);
        }
    }
}
