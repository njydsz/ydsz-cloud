package com.njydsz.common.audit.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
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
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.audit.aspect.AuditAspect;
import com.njydsz.common.audit.core.AsyncAuditRecorder;
import com.njydsz.common.audit.core.AuditFallbackWriter;
import com.njydsz.common.audit.core.AuditMetricsBinder;
import com.njydsz.common.audit.core.AuditQueryService;
import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.core.AuditWriter;
import com.njydsz.common.audit.core.DefaultAuditQueryService;
import com.njydsz.common.audit.core.DefaultAuditRecorder;
import com.njydsz.common.audit.event.AuditEventListener;
import com.njydsz.common.audit.event.GatewayAuditEventBridge;
import com.njydsz.common.audit.health.AuditHealthIndicator;
import com.njydsz.common.audit.storage.DefaultAuditStorage;
import com.njydsz.common.audit.storage.JdbcAuditStorage;
import com.njydsz.common.audit.template.AuditTemplateProcessor;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 审计模块自动配置
 *
 * <p>通过 {@code @EnableYdszAudit} 启用审计模块后，自动注册以下核心 Bean：
 *
 * <ul>
 *   <li>{@link AuditAspect}：审计切面，拦截 {@link com.njydsz.common.audit.annotation.Audit} 注解
 *   <li>{@link AuditTemplateProcessor}：SpEL 模板解析器
 *   <li>{@link AuditWriter}：审计日志写入器（JDBC / 控制台）
 *   <li>{@link AuditRecorder}：异步/同步审计记录器
 *   <li>{@link AuditQueryService}：审计日志查询服务
 *   <li>{@link AuditHealthIndicator}：健康检查指示器
 *   <li>{@link AuditEventListener}：审计事件监听器，消费业务模块发布的 OperationLogEvent / DataExportAuditEvent
 * </ul>
 *
 * <p>优先级与覆盖规则：所有 Bean 均标注 {@code @ConditionalOnMissingBean}， 业务方可提供同名 Bean 进行覆盖。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@RequiredArgsConstructor
@EnableConfigurationProperties(AuditProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.audit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableAsync
public class AuditAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(AuditAutoConfiguration.class);

  /** 异步审计记录器引用，用于优雅停机时调用 shutdown */
  private AsyncAuditRecorder asyncAuditRecorder;

  /**
   * 创建 SpEL 模板处理器 Bean
   *
   * @return SpEL 模板处理器
   */
  @Bean
  @ConditionalOnMissingBean(AuditTemplateProcessor.class)
  public AuditTemplateProcessor auditTemplateProcessor() {
    LOG.info("初始化审计模板处理器: AuditTemplateProcessor");
    return new AuditTemplateProcessor();
  }

  /**
   * 创建 JDBC 审计日志写入器 Bean 当存在 DataSource 且未提供自定义 AuditWriter 时创建
   *
   * @param dataSource 数据源
   * @param properties 审计配置属性
   * @return JDBC 审计日志写入器
   */
  @Bean
  @ConditionalOnMissingBean(AuditWriter.class)
  @ConditionalOnBean(DataSource.class)
  public AuditWriter jdbcAuditWriter(DataSource dataSource, AuditProperties properties) {
    String shardingType = properties.isShardingEnabled() ? properties.getShardingType() : null;
    String baseTableName = properties.getShardingBaseTableName();
    LOG.info(
        "初始化 JDBC 审计日志写入器: JdbcAuditWriter, 分表类型={}, 基础表名={}",
        shardingType != null ? shardingType : "DISABLED",
        baseTableName);
    return new JdbcAuditStorage(dataSource, shardingType, baseTableName);
  }

  /**
   * 创建默认控制台审计日志写入器 Bean 当系统中不存在 DataSource 时降级为控制台输出
   *
   * @return 默认审计日志写入器
   */
  @Bean
  @ConditionalOnMissingBean(AuditWriter.class)
  public AuditWriter defaultAuditWriter() {
    LOG.info("初始化默认审计日志写入器: DefaultAuditStorage(控制台输出)，未检测到 DataSource，降级使用控制台存储");
    return new DefaultAuditStorage();
  }

  /**
   * 创建审计日志切面 Bean
   *
   * @param auditRecorder 审计记录器
   * @param properties 审计配置属性
   * @param templateProcessor SpEL 模板处理器
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return 审计日志切面
   */
  @Bean
  @ConditionalOnMissingBean(AuditAspect.class)
  @ConditionalOnClass(name = "com.njydsz.common.json.YdszJson")
  public AuditAspect auditAspect(
      AuditRecorder auditRecorder,
      AuditProperties properties,
      AuditTemplateProcessor templateProcessor,
      SnowflakeIdGenerator snowflakeIdGenerator) {
    LOG.info("初始化审计日志切面: AuditAspect, 存储策略={}", properties.getStorageType());
    return new AuditAspect(auditRecorder, properties, templateProcessor, snowflakeIdGenerator);
  }

  /**
   * 审计专用异步线程池
   *
   * <p>与主业务线程池隔离，避免审计 IO 影响核心链路。
   *
   * <p>拒绝策略说明：默认使用 {@link ThreadPoolExecutor.CallerRunsPolicy}，当队列满时由调用线程阻塞等待
   * （超时后走磁盘兜底），保证审计留痕完整、不静默丢失。 业务方可通过配置 {@code ydsz.audit.async.reject-policy}
   * 改为丢弃策略以提升吞吐。
   *
   * <p>通过 {@code @ConditionalOnMissingBean} 允许业务方通过 {@code ydsz.thread.pools.auditAsyncExecutor}
   * 注入统一管理线程池覆盖本默认实现。
   *
   * @param properties 审计配置属性
   * @return 异步执行器
   */
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 审计模块基础设置线程池，豁免规范 15.4；业务方可通过
  // ydsz.thread.pools.auditAsyncExecutor 覆盖
  @Bean("auditAsyncExecutor")
  @ConditionalOnMissingBean(name = "auditAsyncExecutor")
  public Executor auditAsyncExecutor(AuditProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    int corePoolSize = properties.getAsync().getThreadCoreSize();
    int maxPoolSize = properties.getAsync().getThreadMaxSize();
    int queueCapacity = properties.getAsync().getQueueCapacity();
    executor.setCorePoolSize(corePoolSize);
    executor.setMaxPoolSize(maxPoolSize);
    executor.setQueueCapacity(queueCapacity);
    // 符合云顶编码规范 15.4.4 命名约定：ydsz-{module}-{biz}-
    executor.setThreadNamePrefix("ydsz-audit-async-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds((int) properties.getAsync().getShutdownTimeout());
    executor.initialize();
    LOG.info(
        "初始化审计异步线程池: core={}, max={}, queue={}, rejectPolicy={}",
        corePoolSize,
        maxPoolSize,
        queueCapacity);
    return executor;
  }

  // CHECKSTYLE.ON: RegexpSinglelineJava

  /**
   * 创建异步审计记录器 Bean 当存在 AuditWriter 且未提供自定义 AuditRecorder 时，使用 LinkedBlockingQueue 实现异步批量写入。
   *
   * @param auditWriter 审计写入器
   * @param properties 审计配置属性
   * @return 异步审计记录器，若不满足条件则返回 null
   */
  @Bean
  @ConditionalOnMissingBean(AuditRecorder.class)
  @ConditionalOnProperty(
      prefix = "ydsz.audit",
      name = "async",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnBean(AuditWriter.class)
  @ConditionalOnClass(name = "com.njydsz.common.json.YdszJson")
  public AuditRecorder asyncAuditRecorder(AuditWriter auditWriter, AuditProperties properties) {
    AuditProperties.AsyncProperties asyncProps = properties.getAsync();

    LOG.info(
        "初始化异步审计记录器: AsyncAuditRecorder, 队列容量={}, 批量阈值={}, 刷新间隔={}ms, 写入器={}",
        asyncProps.getExecutorQueueCapacity(),
        asyncProps.getBatchSize(),
        asyncProps.getBatchIntervalMillis(),
        auditWriter.getName());
    AsyncAuditRecorder recorder = new AsyncAuditRecorder(auditWriter, properties);
    this.asyncAuditRecorder = recorder;
    return recorder;
  }

  /**
   * 创建默认审计记录器 Bean 当系统中不存在 AuditRecorder 类型的 Bean 且未启用异步模式时创建
   *
   * @param auditWriter 审计日志写入器
   * @return 默认审计记录器
   */
  @Bean
  @ConditionalOnMissingBean(AuditRecorder.class)
  @ConditionalOnProperty(
      prefix = "ydsz.audit",
      name = "async",
      havingValue = "false",
      matchIfMissing = false)
  public AuditRecorder auditRecorder(AuditWriter auditWriter) {
    LOG.info("初始化默认审计记录器: DefaultAuditRecorder");
    return new DefaultAuditRecorder(auditWriter);
  }

  /**
   * 创建默认审计查询服务 Bean 需要 DataSource 才可用，用于从数据库查询审计日志
   *
   * @param dataSource 数据源
   * @param properties 审计配置属性
   * @return 默认审计查询服务
   */
  @Bean
  @ConditionalOnMissingBean(AuditQueryService.class)
  @ConditionalOnBean(DataSource.class)
  public AuditQueryService auditQueryService(DataSource dataSource, AuditProperties properties) {
    String shardingType = properties.isShardingEnabled() ? properties.getShardingType() : null;
    String baseTableName = properties.getShardingBaseTableName();
    LOG.info(
        "初始化默认审计查询服务: DefaultAuditQueryService, 分表类型={}",
        shardingType != null ? shardingType : "DISABLED");
    return new DefaultAuditQueryService(dataSource, shardingType, baseTableName);
  }

  /**
   * 创建审计日志磁盘兜底写入器 Bean
   *
   * @return AuditFallbackWriter 实例
   */
  @Bean
  @ConditionalOnMissingBean(AuditFallbackWriter.class)
  public AuditFallbackWriter auditFallbackWriter() {
    return new AuditFallbackWriter();
  }

  /**
   * 创建审计模块 Micrometer 指标绑定器 Bean
   *
   * <p>当存在 AuditRecorder 和 MeterRegistry 时自动注册， 将审计队列大小、使用率、成功/失败计数、写入延迟等指标暴露到 Prometheus。
   *
   * @param auditRecorder 审计记录器
   * @param meterRegistry Micrometer 指标注册中心
   * @return 审计指标绑定器实例
   */
  @Bean
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  @ConditionalOnBean(AuditRecorder.class)
  @ConditionalOnMissingBean(AuditMetricsBinder.class)
  public AuditMetricsBinder auditMetricsBinder(
      AuditRecorder auditRecorder, ObjectProvider<MeterRegistry> meterRegistryProvider) {
    AuditMetricsBinder binder = new AuditMetricsBinder(auditRecorder);
    MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
    if (meterRegistry != null) {
      binder.bindTo(meterRegistry);
      LOG.info("初始化审计指标绑定器: AuditMetricsBinder, 已绑定到 MeterRegistry");
    } else {
      LOG.info("初始化审计指标绑定器: AuditMetricsBinder, MeterRegistry 不可用，指标未绑定");
    }
    return binder;
  }

  /**
   * 创建审计模块健康检查指示器 Bean 当存在 HealthIndicator 类且启用审计模块时创建
   *
   * @param auditRecorder 审计记录器
   * @param properties 审计配置属性
   * @return 审计健康检查指示器
   */
  @Bean
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  @ConditionalOnBean(AuditRecorder.class)
  @ConditionalOnProperty(
      prefix = "ydsz.audit",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @ConditionalOnMissingBean(name = "auditHealthIndicator")
  public AuditHealthIndicator auditHealthIndicator(
      AuditRecorder auditRecorder, AuditProperties properties) {
    LOG.info("初始化审计健康检查指示器: AuditHealthIndicator");
    return new AuditHealthIndicator(auditRecorder, properties);
  }

  /**
   * 创建网关审计事件桥接器 Bean
   *
   * <p>供 Spring Cloud Gateway 等响应式组件使用，将 WebFlux 过滤器中采集的审计数据 安全发布到 Spring 事件体系，由 {@link
   * AuditEventListener} 异步消费并落库。
   *
   * <p>使用场景：
   *
   * <ul>
   *   <li>网关 GlobalFilter 通过本桥接器发布操作日志事件
   *   <li>其他响应式组件（如 WebFlux 端点）需要统一审计时使用
   * </ul>
   *
   * @param eventPublisher Spring 事件发布器
   * @return 网关审计事件桥接器
   */
  @Bean
  @ConditionalOnMissingBean(GatewayAuditEventBridge.class)
  public GatewayAuditEventBridge gatewayAuditEventBridge(ApplicationEventPublisher eventPublisher) {
    LOG.info("初始化网关审计事件桥接器: GatewayAuditEventBridge");
    return new GatewayAuditEventBridge(eventPublisher);
  }

  /**
   * 创建审计事件监听器 Bean
   *
   * <p>消费业务模块通过 {@code ApplicationEventPublisher} 发布的 {@link
   * com.njydsz.common.audit.event.OperationLogEvent} 和 {@link
   * com.njydsz.common.audit.event.DataExportAuditEvent}， 转换为统一的 {@link
   * com.njydsz.common.audit.domain.AuditLog} 并异步落库。
   *
   * <p>前置条件：本类已标注 {@code @EnableAsync}， {@code @Async("auditAsyncExecutor")} 注解自动生效。
   *
   * @param auditRecorder 审计记录器
   * @param snowflakeIdGenerator 分布式 ID 生成器
   * @return 审计事件监听器
   */
  @Bean
  @ConditionalOnMissingBean(AuditEventListener.class)
  @ConditionalOnBean(AuditRecorder.class)
  public AuditEventListener auditEventListener(
      AuditRecorder auditRecorder, SnowflakeIdGenerator snowflakeIdGenerator) {
    LOG.info("初始化审计事件监听器: AuditEventListener");
    return new AuditEventListener(auditRecorder, snowflakeIdGenerator);
  }

  /** 优雅停机时确保异步记录器队列中剩余日志全部写入 */
  @PreDestroy
  public void destroy() {
    if (asyncAuditRecorder != null) {
      LOG.info("审计模块关闭中，执行异步记录器优雅停机...");
      asyncAuditRecorder.shutdown();
    }
  }
}
