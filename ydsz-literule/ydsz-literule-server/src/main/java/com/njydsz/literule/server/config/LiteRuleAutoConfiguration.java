package com.njydsz.literule.server.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.event.service.OutboxService;
import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.search.sync.SearchIndexEventBridge;
import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.api.expression.ExpressionEngine;
import com.njydsz.literule.domain.model.MockModelInputProvider;
import com.njydsz.literule.domain.model.ModelInputProvider;
import com.njydsz.literule.domain.model.ModelInputRegistry;
import com.njydsz.literule.infra.mapper.RuleDefinitionMapper;
import com.njydsz.literule.server.approval.ApprovalPermissionChecker;
import com.njydsz.literule.domain.repository.ApprovalRecordRepository;
import com.njydsz.literule.server.approval.RuleApprovalService;
import com.njydsz.literule.server.approval.RuleApprovalWorkflowBridge;
import com.njydsz.literule.server.audit.RuleAuditLogService;
import com.njydsz.literule.server.benchmark.RuleStressTestService;
import com.njydsz.literule.server.cache.CachingRuleConfigProvider;
import com.njydsz.literule.server.cep.CEPEngine;
import com.njydsz.literule.server.core.AsyncTraceRecorder;
import com.njydsz.literule.server.core.DefaultRuleEngine;
import com.njydsz.literule.server.core.EvaluationResultCache;
import com.njydsz.literule.server.core.InMemoryRuleMetrics;
import com.njydsz.literule.server.core.MicrometerRuleMetrics;
import com.njydsz.literule.server.core.ParallelRuleEvaluator;
import com.njydsz.literule.server.core.RuleCanaryRouter;
import com.njydsz.literule.server.core.RuleCircuitBreaker;
import com.njydsz.literule.server.core.RuleLifecycleService;
import com.njydsz.literule.server.core.RuleMetrics;
import com.njydsz.literule.server.core.RuleTimeoutExecutor;
import com.njydsz.literule.server.distributed.RuleConfigOutboxGateway;
import com.njydsz.literule.server.distributed.RuleConfigOutboxRelay;
import com.njydsz.literule.server.engine.liteexpr.AviatorExpressionEngine;
import com.njydsz.literule.server.expression.EmptyVariableRegistry;
import com.njydsz.literule.server.expression.ExpressionValidationService;
import com.njydsz.literule.server.expression.VariableRegistry;
import com.njydsz.literule.server.health.LiteRuleHealthIndicator;
import com.njydsz.literule.server.orchestrator.RuleChain;
import com.njydsz.literule.server.replay.ExecutionReplayService;
import com.njydsz.literule.server.sdk.LiteRuleSdk;
import com.njydsz.literule.server.security.RulePermissionChecker;
import com.njydsz.literule.server.spi.DbRuleSource;
import com.njydsz.literule.server.spi.DecisionTableConfigProvider;
import com.njydsz.literule.server.spi.DecisionTreeConfigProvider;
import com.njydsz.literule.server.spi.DefaultAlertActionHandler;
import com.njydsz.literule.server.spi.FactProvider;
import com.njydsz.literule.server.spi.FactProviderRegistry;
import com.njydsz.literule.server.spi.FileRuleSource;
import com.njydsz.literule.server.spi.RuleActionDispatcher;
import com.njydsz.literule.server.spi.RuleActionHandler;
import com.njydsz.literule.server.spi.RuleConfigBroadcaster;
import com.njydsz.literule.server.spi.RuleConfigProvider;
import com.njydsz.literule.server.spi.RuleSource;
import com.njydsz.literule.server.spi.RuleSourceManager;
import com.njydsz.literule.domain.repository.RuleVersionRepository;
import com.njydsz.literule.server.spi.ScorecardConfigProvider;
import com.njydsz.literule.server.spi.ScriptConfigProvider;
import com.njydsz.literule.server.spi.TraceRecorder;

/**
 * LiteFlow 规则引擎自动配置。
 *
 * <p>封装 LiteFlow 规则引擎的 Bean 注册：执行器、规则加载器、监控指标、回放服务、A/B 测试分流器。
 *
 * <p>通过 {@code ydsz.literule.*} 配置规则文件路径、组件扫描包、监控启用等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LiteRuleProperties.class)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ydsz.literule",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LiteRuleAutoConfiguration {

  /**
   * 优雅关闭 RuleChain 静态线程池（P1-T4）
   *
   * <p>Spring 容器关闭时调用 {@link RuleChain#shutdownFallbackExecutor()}， 释放 WHEN 链回退线程池资源。
   */
  @PreDestroy
  public void destroy() {
    RuleChain.shutdownFallbackExecutor();
  }

  /**
   * 表达式求值器
   *
   * <p>2.1.0 起仅保留自研 {@link AviatorExpressionEngine}，零外部依赖、AST 原生追踪/沙箱/变量提取。
   *
   * @param properties 配置属性
   * @return ExpressionEngine 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public ExpressionEngine expressionEvaluator(LiteRuleProperties properties) {
    log.info("[LiteRule] LiteExpr 自研表达式求值器已初始化（sandbox={}）", properties.isSandboxEnabled());
    return new AviatorExpressionEngine(properties.isSandboxEnabled());
  }

  /**
   * 规则引擎
   *
   * <p>1.4.0 起：
   *
   * <ul>
   *   <li>当 traceEnabled=true 时自动装配 {@link AsyncTraceRecorder}， 若消费方提供了自定义 {@link TraceRecorder}
   *       Bean 则作为持久化委托
   *   <li>当 ruleTimeoutMs > 0 时启用 {@link RuleTimeoutExecutor}
   *   <li>当 circuitBreakerMinEvaluations > 0 时启用 {@link RuleCircuitBreaker}（P2-14: openStateMs 由
   *       {@code ydsz.literule.circuit-breaker-open-state-ms} 配置）
   *   <li>当 MeterRegistry 可用时启用 {@link MicrometerRuleMetrics}
   *   <li>当 canaryEnabled=true 时启用 {@link RuleCanaryRouter}
   * </ul>
   *
   * @param properties 配置属性
   * @param traceDelegateProvider 持久化委托提供者（可选）
   * @param meterRegistryProvider Micrometer 注册器（可选）
   * @return DefaultRuleEngine 实例
   */
  @Bean
  @ConditionalOnMissingBean
  public RuleEngine ruleEngine(
      LiteRuleProperties properties,
      ObjectProvider<TraceRecorder> traceDelegateProvider,
      ObjectProvider<ExpressionEngine> evaluatorProvider,
      ObjectProvider<ModelInputRegistry> modelRegistryProvider,
      ObjectProvider<FactProviderRegistry> factRegistryProvider,
      ObjectProvider<RuleActionDispatcher> actionDispatcherProvider,
      ObjectProvider<ParallelRuleEvaluator> parallelEvaluatorProvider,
      ObjectProvider<MeterRegistry> meterRegistryProvider,
      ApplicationContext applicationContext) {
    DefaultRuleEngine engine = new DefaultRuleEngine();
    engine.setStatsEnabled(properties.isStatsEnabled());

    // P3-3: 拆分为独立配置方法，提升可读性
    configureOptionalDependencies(
        engine,
        factRegistryProvider,
        actionDispatcherProvider,
        parallelEvaluatorProvider,
        modelRegistryProvider,
        properties);
    configureTraceRecorder(engine, properties, traceDelegateProvider);
    configureTimeoutExecutor(engine, properties, applicationContext);
    configureCircuitBreaker(engine, properties);
    configureCanaryRouting(engine, properties, evaluatorProvider);

    // Micrometer 桥接（仅当 classpath 存在 MeterRegistry 时启用）
    bindMicrometerIfAvailable(engine, meterRegistryProvider);

    // P1-7: 评估结果缓存
    if (properties.getPerformance().isCacheEnabled()) {
      EvaluationResultCache cache =
          new EvaluationResultCache(
              properties.getPerformance().getCacheTtlSeconds() * 1000L,
              properties.getPerformance().getCacheMaxSize());
      engine.setEvaluationResultCache(cache);
      log.info(
          "[LiteRule] 评估结果缓存已启用 (ttl={}s, maxSize={})",
          properties.getPerformance().getCacheTtlSeconds(),
          properties.getPerformance().getCacheMaxSize());
    }

    log.info(
        "[LiteRule] 默认规则引擎已初始化（statsEnabled={}, traceEnabled={}, timeoutMs={}, breaker={}, metrics={}, canary={}, model={}, slowRuleThreshold={}ms）",
        properties.isStatsEnabled(),
        properties.isTraceEnabled(),
        properties.getRuleTimeoutMs(),
        properties.getCircuitBreakerMinEvaluations() > 0,
        engine.getMetrics() != null,
        engine.getCanaryRouter() != null,
        engine.getModelInputRegistry() != null,
        properties.getPerformance().getSlowRuleThresholdMs());
    return engine;
  }

  /** 配置可选依赖（P3-3 提取） */
  private void configureOptionalDependencies(
      DefaultRuleEngine engine,
      ObjectProvider<FactProviderRegistry> factRegistryProvider,
      ObjectProvider<RuleActionDispatcher> actionDispatcherProvider,
      ObjectProvider<ParallelRuleEvaluator> parallelEvaluatorProvider,
      ObjectProvider<ModelInputRegistry> modelRegistryProvider,
      LiteRuleProperties properties) {
    FactProviderRegistry factRegistry = factRegistryProvider.getIfAvailable();
    if (factRegistry != null) {
      engine.setFactProviderRegistry(factRegistry);
    }

    RuleActionDispatcher actionDispatcher = actionDispatcherProvider.getIfAvailable();
    if (actionDispatcher != null) {
      engine.setActionDispatcher(actionDispatcher);
    }

    ParallelRuleEvaluator parallelEvaluator = parallelEvaluatorProvider.getIfAvailable();
    if (parallelEvaluator != null) {
      engine.setParallelEvaluator(parallelEvaluator);
      engine.setParallelThreshold(properties.getPerformance().getParallelThreshold());
    }

    long slowRuleThresholdMs = properties.getPerformance().getSlowRuleThresholdMs();
    if (slowRuleThresholdMs > 0) {
      engine.setSlowRuleThresholdMs(slowRuleThresholdMs);
    }

    ModelInputRegistry modelRegistry = modelRegistryProvider.getIfAvailable();
    if (modelRegistry != null) {
      engine.setModelInputRegistry(modelRegistry);
    }
  }

  /** 配置 Trace 记录器（P3-3 提取） */
  private void configureTraceRecorder(
      DefaultRuleEngine engine,
      LiteRuleProperties properties,
      ObjectProvider<TraceRecorder> traceDelegateProvider) {
    if (!properties.isTraceEnabled()) return;
    AsyncTraceRecorder asyncRecorder =
        new AsyncTraceRecorder(
            properties.getTraceQueueCapacity(),
            properties.getTraceBatchSize(),
            properties.getTraceFlushIntervalMs());
    TraceRecorder delegate = traceDelegateProvider.getIfAvailable();
    if (delegate != null && !(delegate instanceof AsyncTraceRecorder)) {
      asyncRecorder.setDelegate(delegate);
      log.info("[LiteRule] Trace 持久化委托已注入: {}", delegate.getClass().getSimpleName());
    }
    engine.setTraceRecorder(asyncRecorder);
    log.info(
        "[LiteRule] 异步 Trace 记录已启用 (queueCapacity={}, batchSize={}, flushMs={}, delegate={})",
        properties.getTraceQueueCapacity(),
        properties.getTraceBatchSize(),
        properties.getTraceFlushIntervalMs(),
        delegate != null);
  }

  /** 配置超时执行器（P3-3 提取） */
  private void configureTimeoutExecutor(
      DefaultRuleEngine engine,
      LiteRuleProperties properties,
      ApplicationContext applicationContext) {
    if (properties.getRuleTimeoutMs() <= 0) return;
    // P1-2: 使用 common-thread 统一管理的线程池（ydsz.thread.pools.ruleTimeout）
    ThreadPoolTaskExecutor threadPool = lookupExecutor(applicationContext, "ruleTimeoutExecutor");
    RuleTimeoutExecutor timeoutExecutor;
    if (threadPool != null) {
      timeoutExecutor = new RuleTimeoutExecutor(properties.getRuleTimeoutMs(), threadPool);
      log.info(
          "[LiteRule] 单规则超时控制已启用 (timeoutMs={}, executor=common-thread:ruleTimeout)",
          properties.getRuleTimeoutMs());
    } else {
      // common-thread 未配置时降级（使用可用处理器数）
      int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
      timeoutExecutor =
          new RuleTimeoutExecutor(
              properties.getRuleTimeoutMs(), createFallbackTimeoutExecutor(poolSize));
      log.warn(
          "[LiteRule] 单规则超时控制已启用 (timeoutMs={}, poolSize={}, executor=fallback: common-thread bean 'ruleTimeoutExecutor' 未配置)",
          properties.getRuleTimeoutMs(),
          poolSize);
    }
    engine.setTimeoutExecutor(timeoutExecutor);
  }

  /**
   * 创建降级守护线程池（common-thread 未配置时的兜底方案）。
   *
   * <p><b>注意：</b>此方法仅在 common-thread 未配置 {@code ydsz.thread.pools.ruleTimeout} 时兜底使用，
   * 生产环境应通过配置 {@code ydsz.thread.pools.ruleTimeout.core-size} 等属性启用统一线程池管理。
   *
   * <p>线程设置为守护线程，避免阻止 JVM 退出；线程名带 {@code literule-timeout-} 前缀便于排查。
   *
   * @param poolSize 线程池大小
   * @return 守护线程池
   * @since 1.0.0
   */
  @SuppressWarnings("PMD.AvoidThreadGroup")
  private static ExecutorService createFallbackTimeoutExecutor(int poolSize) {
    // CHECKSTYLE.OFF: RegexpSinglelineJava - 降级兜底，common-thread 未配置时使用
    ThreadFactory factory =
        new ThreadFactory() {
          private final AtomicInteger counter = new AtomicInteger(0);

          @Override
          public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "literule-timeout-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
          }
        };
    return new ThreadPoolExecutor(
        poolSize,
        poolSize,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(1024),
        factory,
        new ThreadPoolExecutor.CallerRunsPolicy());
    // CHECKSTYLE.ON: RegexpSinglelineJava
  }

  /**
   * P1-2: 从 Spring 容器中查找 common-thread 注册的线程池 Bean
   *
   * @param applicationContext Spring 上下文
   * @param beanName Bean 名称（key + "Executor"）
   * @return 线程池实例；不存在时返回 null
   */
  private ThreadPoolTaskExecutor lookupExecutor(
      ApplicationContext applicationContext, String beanName) {
    try {
      return applicationContext.getBean(beanName, ThreadPoolTaskExecutor.class);
    } catch (Exception e) {
      return null;
    }
  }

  /** 配置熔断器（P3-3 提取） */
  private void configureCircuitBreaker(DefaultRuleEngine engine, LiteRuleProperties properties) {
    if (properties.getCircuitBreakerMinEvaluations() <= 0) return;
    RuleCircuitBreaker breaker =
        new RuleCircuitBreaker(
            properties.getCircuitBreakerErrorRate(),
            properties.getCircuitBreakerMinEvaluations(),
            properties.getCircuitBreakerOpenStateMs());
    engine.setCircuitBreaker(breaker);
    log.info(
        "[LiteRule] 规则熔断器已启用 (errorRateThreshold={}, minEvaluations={}, openStateMs={})",
        properties.getCircuitBreakerErrorRate(),
        properties.getCircuitBreakerMinEvaluations(),
        properties.getCircuitBreakerOpenStateMs());
  }

  /** 配置灰度路由（P3-3 提取） */
  private void configureCanaryRouting(
      DefaultRuleEngine engine,
      LiteRuleProperties properties,
      ObjectProvider<ExpressionEngine> evaluatorProvider) {
    if (!properties.isCanaryEnabled()) return;
    ExpressionEngine evaluator = evaluatorProvider.getIfAvailable();
    if (evaluator == null) {
      evaluator = expressionEvaluator(properties);
    }
    RuleCanaryRouter canaryRouter = new RuleCanaryRouter(evaluator);
    engine.setCanaryRouter(canaryRouter);
    engine.setCanaryEnabled(true);
    log.info("[LiteRule] 规则灰度路由已启用");
  }

  /**
   * 当 classpath 存在 MeterRegistry 时桥接到 Micrometer（P1-T3：从反射改为 ObjectProvider 注入）
   *
   * <p>使用 {@link ObjectProvider} 可选注入，避免反射式检测的{ 性能开销和类型不安全。ObjectProvider 在 MeterRegistry 不在
   * classpath 或无 Bean 注册时返回 null，天然支持可选依赖。
   */
  private void bindMicrometerIfAvailable(
      DefaultRuleEngine engine, ObjectProvider<MeterRegistry> meterRegistryProvider) {
    MeterRegistry registry = meterRegistryProvider.getIfAvailable();
    if (registry == null) {
      log.debug("[LiteRule] MeterRegistry 未注入，使用内存计数器降级");
      engine.setMetrics(new InMemoryRuleMetrics());
      return;
    }
    try {
      // P2: MicrometerRuleMetrics 继承 SentryMetricsAdapter，不再直传 MeterRegistry
      RuleMetrics metrics = new MicrometerRuleMetrics();
      engine.setMetrics(metrics);
      log.info("[LiteRule] Prometheus 监控指标已启用 (通过 SentryMetricsAdapter 桥接)");
    } catch (Exception e) {
      log.warn("[LiteRule] MicrometerRuleMetrics 桥接失败: {}", e.getMessage());
    }
  }

  /**
   * A/B 测试服务
   *
   * @param evaluator 表达式求值器
   * @return ABTestService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  public ABTestService abTestService(ExpressionEngine evaluator) {
    log.info("[LiteRule] A/B 测试服务已初始化");
    return new ABTestService(evaluator);
  }

  /**
   * 表达式校验服务（1.4.0 起支持）
   *
   * <p>面向前端表达式编辑器的校验 API，提供结构化的错误信息。 当 classpath 中存在 {@link VariableRegistry} Bean 时， 启用
   * UNDEFINED_VARIABLE 校验；否则使用 {@link EmptyVariableRegistry} 跳过。
   *
   * @param evaluator 表达式求值器
   * @param registryProvider 变量注册表（可选）
   * @return ExpressionValidationService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  public ExpressionValidationService expressionValidationService(
      ExpressionEngine evaluator, ObjectProvider<VariableRegistry> registryProvider) {
    VariableRegistry registry = registryProvider.getIfAvailable();
    if (registry == null) {
      registry = new EmptyVariableRegistry();
      log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验未启用）");
    } else {
      log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验已启用，已注册 {} 个变量）", registry.listAll().size());
    }
    return new ExpressionValidationService(evaluator, registry);
  }

  /**
   * 规则热加载管理器（当存在 RuleConfigProvider 时生效）
   *
   * <p>1.4.0 起支持以下可选 SPI：决策表/评分卡/决策树/脚本规则的动态加载。
   *
   * @param ruleEngine 规则引擎
   * @param evaluator 表达式求值器
   * @param configProvider 规则配置提供者
   * @param dtConfigProvider 决策表配置提供者（可选）
   * @param scConfigProvider 评分卡配置提供者（可选）
   * @param trConfigProvider 决策树配置提供者（可选）
   * @param scriptConfigProvider 脚本规则配置提供者（可选）
   * @param properties 配置属性
   * @return RuleHotReloader 实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleConfigProvider.class)
  public RuleHotReloader ruleHotReloader(
      RuleEngine ruleEngine,
      ExpressionEngine evaluator,
      RuleConfigProvider configProvider,
      ObjectProvider<DecisionTableConfigProvider> dtConfigProvider,
      ObjectProvider<ScorecardConfigProvider> scConfigProvider,
      ObjectProvider<DecisionTreeConfigProvider> trConfigProvider,
      ObjectProvider<ScriptConfigProvider> scriptConfigProvider,
      LiteRuleProperties properties) {
    RuleHotReloader reloader =
        new RuleHotReloader(ruleEngine, evaluator, configProvider, properties);

    DecisionTableConfigProvider dt = dtConfigProvider.getIfAvailable();
    if (dt != null) {
      reloader.setDecisionTableConfigProvider(dt);
    }

    ScorecardConfigProvider sc = scConfigProvider.getIfAvailable();
    if (sc != null) {
      reloader.setScorecardConfigProvider(sc);
    }

    DecisionTreeConfigProvider tr = trConfigProvider.getIfAvailable();
    if (tr != null) {
      reloader.setDecisionTreeConfigProvider(tr);
    }

    ScriptConfigProvider script = scriptConfigProvider.getIfAvailable();
    if (script != null) {
      reloader.setScriptConfigProvider(script);
    }

    log.info(
        "[LiteRule] 规则热加载管理器已初始化（hotReload={}, decisionTable={}, scorecard={}, decisionTree={}, script={}）",
        properties.isHotReloadEnabled(),
        dt != null,
        sc != null,
        tr != null,
        script != null);
    return reloader;
  }

  /**
   * 决策表管理服务（当存在 DecisionTableConfigProvider 时生效）
   *
   * @param ruleEngine 规则引擎
   * @param dtConfigProvider 决策表配置提供者
   * @param broadcasterProvider 广播器（可选）
   * @param eventPublisher 事件发布器
   * @return DecisionTableAdminService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(DecisionTableConfigProvider.class)
  public DecisionTableAdminService decisionTableAdminService(
      RuleEngine ruleEngine,
      DecisionTableConfigProvider dtConfigProvider,
      ObjectProvider<RuleConfigBroadcaster> broadcasterProvider,
      ApplicationEventPublisher eventPublisher) {
    DecisionTableAdminService service =
        new DecisionTableAdminService(ruleEngine, dtConfigProvider, eventPublisher);
    RuleConfigBroadcaster broadcaster = broadcasterProvider.getIfAvailable();
    if (broadcaster != null) {
      service.setBroadcaster(broadcaster);
    }
    log.info("[LiteRule] 决策表管理服务已初始化（broadcast={}）", broadcaster != null);
    return service;
  }

  /**
   * 规则管理服务（当存在 RuleConfigProvider 时生效）
   *
   * @param ruleEngine 规则引擎
   * @param evaluator 表达式求值器
   * @param configProvider 规则配置提供者
   * @param eventPublisher 事件发布器
   * @return RuleAdminService 实例
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleConfigProvider.class)
  public RuleAdminService ruleAdminService(
      RuleEngine ruleEngine,
      ExpressionEngine evaluator,
      RuleConfigProvider configProvider,
      ObjectProvider<RuleVersionRepository> versionRepoProvider,
      ObjectProvider<RuleConfigBroadcaster> broadcasterProvider,
      ObjectProvider<SearchIndexEventBridge> searchIndexEventBridgeProvider,
      ObjectProvider<OutboxService> outboxServiceProvider,
      ApplicationEventPublisher eventPublisher,
      LiteRuleProperties properties,
      RuleDefinitionMapper ruleDefinitionMapper) {
    RuleAdminService service =
        new RuleAdminService(
            ruleEngine,
            evaluator,
            configProvider,
            versionRepoProvider.getIfAvailable(),
            eventPublisher,
            ruleDefinitionMapper);
    service.setDryRunEnabled(properties.isDryRunEnabled());
    RuleConfigBroadcaster broadcaster = broadcasterProvider.getIfAvailable();
    if (broadcaster != null) {
      service.setBroadcaster(broadcaster);
      log.info("[LiteRule] 分布式规则广播已启用");
    }
    // P0-A1: 事务性 Outbox（可选，未引入 common-event 时自动跳过）
    OutboxService outboxService = outboxServiceProvider.getIfAvailable();
    if (outboxService != null) {
      service.setOutboxService(outboxService);
      log.info("[LiteRule] 规则变更事件已启用 Outbox 事务性广播（可失败重试）");
    }
    // 搜索索引同步（可选，未引入 common-search 时自动跳过）
    service.setSearchIndexEventBridgeProvider(searchIndexEventBridgeProvider);
    // 冲突检测（1.4.0 起支持，仅在启用时装配检测器）
    if (properties.isConflictDetectionEnabled()) {
      RuleConflictDetector conflictDetector = new RuleConflictDetector(configProvider);
      service.setConflictDetector(conflictDetector);
      service.setConflictDetectionEnabled(true);
      service.setConflictDetectionBlockOnError(properties.isConflictDetectionBlockOnError());
      log.info(
          "[LiteRule] 规则冲突检测已启用（blockOnError={}）", properties.isConflictDetectionBlockOnError());
    } else {
      // 显式关闭：即便上层手动注入检测器，也不生效
      service.setConflictDetectionEnabled(false);
    }
    log.info(
        "[LiteRule] 规则管理服务已初始化（dryRun={}, broadcast={}, conflictDetection={}）",
        properties.isDryRunEnabled(),
        broadcaster != null,
        properties.isConflictDetectionEnabled());
    return service;
  }

  /**
   * 规则配置变更 Outbox 中继（P0-A1 热更新一致性）
   *
   * <p>事务提交后捕获 OutboxMessage 中的规则刷新事件，执行低延迟广播（毫秒级）， 成功即标记 SENT；失败保持
   * PENDING 交由 OutboxProcessor 兜底重试。 当广播器与 Outbox 仓储均存在时自动装配。
   *
   * @param broadcasterProvider 规则配置广播器（可选）
   * @param outboxRepositoryProvider Outbox 仓储（可选）
   * @return RuleConfigOutboxRelay 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleConfigBroadcaster.class)
  public RuleConfigOutboxRelay ruleConfigOutboxRelay(
      RuleConfigBroadcaster broadcaster,
      ObjectProvider<OutboxRepository> outboxRepositoryProvider,
      ObjectProvider<String> nodeIdProvider) {
    String nodeId = nodeIdProvider.getIfAvailable();
    if (nodeId == null) {
      nodeId = "literule-outbox-relay";
    }
    RuleConfigOutboxRelay relay =
        new RuleConfigOutboxRelay(broadcaster, outboxRepositoryProvider.getIfAvailable(), nodeId);
    log.info("[LiteRule] 规则变更 Outbox 中继已初始化（低延迟广播 + 失败重试兜底）");
    return relay;
  }

  /**
   * 规则配置变更 Outbox 投递网关（P0-A1 热更新一致性）
   *
   * <p>实现 {@link EventPublishGateway}，使 OutboxProcessor 轮询到 PENDING 的规则刷新消息时 能真正投递到
   * Redis Pub/Sub（替代 Noop 网关），广播失败自动指数退避重试。 仅当容器中无其他事件投递网关时注册，避免与业务方
   * RocketMQ/Kafka 网关冲突。
   *
   * @param broadcaster 规则配置广播器
   * @param nodeIdProvider 当前节点标识（可选）
   * @return RuleConfigOutboxGateway 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean(EventPublishGateway.class)
  @ConditionalOnBean(RuleConfigBroadcaster.class)
  public EventPublishGateway ruleConfigOutboxGateway(
      RuleConfigBroadcaster broadcaster, ObjectProvider<String> nodeIdProvider) {
    String nodeId = nodeIdProvider.getIfAvailable();
    if (nodeId == null) {
      nodeId = "literule-outbox-gateway";
    }
    log.info("[LiteRule] 规则变更 Outbox 投递网关已注册（Redis Pub/Sub）");
    return new RuleConfigOutboxGateway(broadcaster, nodeId);
  }

  /**
   * 规则审批流服务（P1-3 多级审批流）
   *
   * <p>当存在 {@link RuleConfigProvider} 时自动装配。默认注册 2 级审批流 （default-2level），消费方可通过 {@link
   * RuleApprovalService#registerFlow} 注册自定义审批流。审批记录默认内存存储，可通过 {@link ApprovalRecordRepository} SPI
   * 提供持久化实现；权限校验可通过 {@link ApprovalPermissionChecker} SPI 委托给消费方。
   *
   * @param configProvider 规则配置提供者
   * @param recordRepoProvider 审批记录持久化仓库（可选）
   * @param permissionCheckerProvider 权限检查器（可选）
   * @return RuleApprovalService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleConfigProvider.class)
  public RuleApprovalService ruleApprovalService(
      RuleConfigProvider configProvider,
      ObjectProvider<ApprovalRecordRepository> recordRepoProvider,
      ObjectProvider<ApprovalPermissionChecker> permissionCheckerProvider,
      ObjectProvider<RuleApprovalWorkflowBridge> workflowBridgeProvider) {
    RuleApprovalService service = new RuleApprovalService(configProvider);
    ApprovalRecordRepository recordRepo = recordRepoProvider.getIfAvailable();
    if (recordRepo != null) {
      service.setRecordRepository(recordRepo);
    }
    ApprovalPermissionChecker checker = permissionCheckerProvider.getIfAvailable();
    if (checker != null) {
      service.setPermissionChecker(checker);
    }
    // P2-1: 注入工作流桥接（可选，由消费方提供实现）
    RuleApprovalWorkflowBridge workflowBridge = workflowBridgeProvider.getIfAvailable();
    if (workflowBridge != null) {
      service.setWorkflowBridge(workflowBridge);
    }
    log.info(
        "[LiteRule-Approval] 规则审批流服务已初始化（recordRepository={}, permissionChecker={}, workflowBridge={}）",
        recordRepo != null,
        checker != null,
        workflowBridge != null);
    return service;
  }

  // ------------------------------------------------------------------
  // P0-7 规则权限检查器
  // ------------------------------------------------------------------

  /**
   * 规则权限检查器 Bean（P0-7）
   *
   * <p>当存在 {@link RuleConfigProvider} 时自动装配，提供按规则分类路径的 细粒度权限校验能力。消费方（如 RuleAdminService）可选注入本接口。
   *
   * @param configProvider 规则配置提供者
   * @return RulePermissionChecker 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleConfigProvider.class)
  public RulePermissionChecker rulePermissionChecker(RuleConfigProvider configProvider) {
    log.info("[LiteRule-Permission] 规则权限检查器已初始化");
    return new RulePermissionChecker(configProvider);
  }

  // ------------------------------------------------------------------
  // P1-1 多级缓存（Caffeine + Redis）
  // ------------------------------------------------------------------

  /**
   * 多级缓存 RuleConfigProvider 装饰器（P1-1）
   *
   * <p>当 classpath 存在 {@link RuleConfigProvider} 实现且 {@code ydsz.literule.cache.enabled=true}（默认
   * true）时， 自动装饰委托 Provider 为 {@link CachingRuleConfigProvider}， 启用 Caffeine（L1 本地）+ Redis（L2
   * 分布式）两级缓存，减少 DB 压力。
   *
   * <p>L2 启用条件：
   *
   * <ul>
   *   <li>classpath 存在 {@code RedissonClient}（通过 {@code ObjectProvider} 安全获取）
   *   <li>{@code ydsz.literule.cache.l2-enabled=true}（默认 true）
   * </ul>
   *
   * 任一不满足则仅启用 L1。
   *
   * <p>使用 {@link Primary} 确保其他组件 （{@link RuleHotReloader} / {@link RuleAdminService}）注入的是缓存装饰器而非原始
   * Provider。
   *
   * @param providers 所有 RuleConfigProvider Bean（过滤掉 CachingRuleConfigProvider 自身）
   * @param redissonClientProvider Redisson 客户端（可选，不存在时降级为仅 L1）
   * @param properties 配置属性
   * @return CachingRuleConfigProvider 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean(CachingRuleConfigProvider.class)
  @ConditionalOnBean(RuleConfigProvider.class)
  @ConditionalOnProperty(
      prefix = "ydsz.literule.cache",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  @Primary
  public CachingRuleConfigProvider cachingRuleConfigProvider(
      List<RuleConfigProvider> providers,
      ObjectProvider<RedissonClient> redissonClientProvider,
      LiteRuleProperties properties) {
    // 过滤掉 CachingRuleConfigProvider 自身（避免循环装饰），取第一个作为委托
    RuleConfigProvider delegate =
        providers.stream()
            .filter(p -> !(p instanceof CachingRuleConfigProvider))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("未找到可装饰的 RuleConfigProvider 委托实现"));
    RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
    log.info(
        "[LiteRule-Cache] 多级缓存 RuleConfigProvider 已初始化 (delegate={}, L2={})",
        delegate.getClass().getSimpleName(),
        redissonClient != null);
    return new CachingRuleConfigProvider(delegate, redissonClient, properties);
  }

  // ------------------------------------------------------------------
  // CEP 复杂事件处理引擎（P0-2）
  // ------------------------------------------------------------------

  /**
   * CEP 引擎 Bean
   *
   * <p>默认装配为单例，业务侧通过 {@link CEPEngine#feed} 投递事件、通过 {@link CEPEngine#registerPattern} 注册模式。命中模式后通过
   * Listener 回调触发关联规则。
   *
   * <p>可通过 {@code ydsz.literule.cep.enabled=false} 关闭。
   *
   * @return CEPEngine 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.literule.cep",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public CEPEngine cepEngine(ExpressionEngine evaluator) {
    CEPEngine engine = new CEPEngine(evaluator);
    log.info("[LiteRule-CEP] 复杂事件处理引擎已初始化");
    return engine;
  }

  // ------------------------------------------------------------------
  // P0-4 健康检查（@Bean 注册，替代 @Component）
  // ------------------------------------------------------------------

  /**
   * 规则引擎健康检查指标 Bean（P0-4）
   *
   * <p>从 @Component 改为 @Bean 注册，与项目其他模块规范一致。
   *
   * @param ruleEngine 规则引擎
   * @param cepEngine CEP 引擎
   * @return LiteRuleHealthIndicator 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
  @ConditionalOnProperty(
      prefix = "ydsz.literule",
      name = "health-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LiteRuleHealthIndicator liteRuleHealthIndicator(
      DefaultRuleEngine ruleEngine, ObjectProvider<CEPEngine> cepEngineProvider) {
    log.info("[LiteRule-Health] 规则引擎健康检查已初始化");
    return new LiteRuleHealthIndicator(ruleEngine, cepEngineProvider.getIfAvailable());
  }

  // ------------------------------------------------------------------
  // P1-11 规则数据源注册（DbRuleSource + RuleSourceManager）
  // ------------------------------------------------------------------

  /**
   * 数据库规则数据源 Bean（P1-11）
   *
   * <p>当 {@code RuleConfigProvider} 存在且 {@code ydsz.literule.rule-source.type=db}（默认）时 自动装配 {@link
   * DbRuleSource}，代理 {@link RuleConfigProvider} 作为默认数据源。
   *
   * @param configProvider 规则配置提供者
   * @return DbRuleSource 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean(RuleSource.class)
  @ConditionalOnBean(RuleConfigProvider.class)
  @ConditionalOnProperty(
      prefix = "ydsz.literule.rule-source",
      name = "type",
      havingValue = "db",
      matchIfMissing = true)
  public DbRuleSource dbRuleSource(RuleConfigProvider configProvider) {
    log.info("[LiteRule-Source] 数据库规则数据源已初始化");
    return new DbRuleSource(configProvider);
  }

  /**
   * 规则数据源管理器 Bean（P1-11）
   *
   * <p>当存在 {@link RuleSource} Bean 时自动装配，管理多个数据源并提供统一切换能力。 自动注册所有 {@link RuleSource}
   * Bean，首个可用数据源设为主数据源。
   *
   * @param sources 所有 RuleSource Bean
   * @return RuleSourceManager 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleSource.class)
  public RuleSourceManager ruleSourceManager(List<RuleSource> sources) {
    RuleSourceManager manager = new RuleSourceManager();
    for (RuleSource source : sources) {
      manager.registerSource(source);
    }
    log.info("[LiteRule-Source] 规则数据源管理器已初始化（sources={}）", sources.size());
    return manager;
  }

  // ------------------------------------------------------------------
  // 声明式规则注解（P2-10）
  // ------------------------------------------------------------------

  /**
   * 声明式规则注册器（P2-10）
   *
   * <p>容器刷新完成后扫描 {@code @LiteRule}（标注在 Rule Bean 上）与
   * {@code @RuleDefinitionMeta}（纯声明式表达式规则）并自动注册到引擎。 通过 {@code
   * ydsz.literule.annotation-scan-base-packages} 指定扫描基包（逗号分隔）。
   *
   * @return LiteRuleAnnotationRegistrar 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  public LiteRuleAnnotationRegistrar liteRuleAnnotationRegistrar(
      RuleEngine ruleEngine,
      ExpressionEngine evaluator,
      ApplicationContext applicationContext,
      LiteRuleProperties properties) {
    LiteRuleAnnotationRegistrar registrar =
        new LiteRuleAnnotationRegistrar(ruleEngine, evaluator, applicationContext, properties);
    log.info(
        "[LiteRule-Annotation] 声明式规则注册器已初始化（scanBasePackages={}）",
        properties.getAnnotationScanBasePackages());
    return registrar;
  }

  // ------------------------------------------------------------------
  // P2-3 DSL YAML/JSON 规则文件加载（FileRuleSource）
  // ------------------------------------------------------------------

  /**
   * 文件规则数据源 Bean（P2-3）
   *
   * <p>当 {@code ydsz.literule.file-source.enabled=true} 时自动装配 {@link FileRuleSource}，从 classpath
   * 或文件系统 加载 YAML/JSON 规则文件。加载后可配合 {@link RuleHotReloader} 注册到引擎。
   *
   * <p>Bean 初始化时调用 {@code init()}，销毁时调用 {@code destroy()} 释放 WatchService。
   *
   * @param properties 配置属性
   * @return FileRuleSource 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.literule.file-source",
      name = "enabled",
      havingValue = "true")
  public FileRuleSource fileRuleSource(LiteRuleProperties properties) {
    LiteRuleProperties.FileSourceConfig cfg = properties.getFileSource();
    FileRuleSource source = new FileRuleSource(cfg.getLocation(), cfg.isWatch());
    try {
      source.init();
      log.info(
          "[LiteRule-FileSource] 文件规则源已初始化（location={}, watch={}, rules={}）",
          cfg.getLocation(),
          cfg.isWatch(),
          source.loadAllRules().size());
    } catch (Exception e) {
      log.error("[LiteRule-FileSource] 文件规则源初始化失败: {}", e.getMessage(), e);
      throw new IllegalStateException("FileRuleSource 初始化失败: " + e.getMessage(), e);
    }
    return source;
  }

  // ------------------------------------------------------------------
  // P3-1 规则+模型融合
  // ------------------------------------------------------------------

  /**
   * 模型输入注册表 Bean（P3-1）
   *
   * <p>当 {@code ydsz.literule.model.enabled=true} 时自动装配，聚合所有 {@link ModelInputProvider} Bean（包括可选的
   * {@link MockModelInputProvider}）。 注册表会自动注入到 {@link DefaultRuleEngine}，使规则表达式可通过 {@code
   * model.<field>} 引用模型输出（如 {@code model.score > 0.8}）。
   *
   * <p>对标滴滴 Newton、字节风控的"规则+模型融合"能力：
   *
   * <ul>
   *   <li>规则兜底模型异常：模型不可用时降级为纯规则评估
   *   <li>模型输出触发规则：模型输出作为规则条件输入
   * </ul>
   *
   * @param properties 配置属性
   * @param providersProvider 所有 ModelInputProvider Bean（可选，含 Mock）
   * @return ModelInputRegistry 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "ydsz.literule.model", name = "enabled", havingValue = "true")
  public ModelInputRegistry modelInputRegistry(
      LiteRuleProperties properties,
      ObjectProvider<ModelInputProvider> providersProvider,
      ApplicationContext applicationContext) {
    LiteRuleProperties.ModelConfig cfg = properties.getModel();
    // P1-2: 优先使用 common-thread 统一管理的线程池
    ThreadPoolTaskExecutor threadPool = lookupExecutor(applicationContext, "modelInputExecutor");
    ModelInputRegistry registry;
    if (threadPool != null) {
      registry =
          new ModelInputRegistry(
              cfg.getTimeoutMs(), cfg.isFallbackOnError(), threadPool.getThreadPoolExecutor());
    } else {
      // CHECKSTYLE.OFF: RegexpSinglelineJava - 降级兜底，common-thread 未配置时使用
      registry = new ModelInputRegistry(cfg.getTimeoutMs(), cfg.isFallbackOnError());
      // CHECKSTYLE.ON: RegexpSinglelineJava
      log.warn("[LiteRule-Model] common-thread bean 'modelInputExecutor' 未配置，使用降级线程池");
    }
    // 注册所有 ModelInputProvider Bean（包括 MockModelInputProvider）
    List<ModelInputProvider> providers = providersProvider.orderedStream().toList();
    for (ModelInputProvider provider : providers) {
      registry.register(provider);
    }
    log.info(
        "[LiteRule-Model] 模型输入注册表已初始化 (providers={}, timeoutMs={}, fallbackOnError={})",
        registry.size(),
        cfg.getTimeoutMs(),
        cfg.isFallbackOnError());
    return registry;
  }

  /**
   * Mock 模型输入提供者 Bean（P3-1）
   *
   * <p>当 {@code ydsz.literule.model.mock-enabled=true} 时自动装配，返回配置的
   * 模拟模型输出，便于开发/测试环境验证规则+模型融合能力，无需依赖真实模型服务。
   *
   * <p>输出可通过 {@code ydsz.literule.model.mock-outputs} 配置自定义：
   *
   * <pre>
   * ydsz:
   *   literule:
   *     model:
   *       enabled: true
   *       mock-enabled: true
   *       mock-outputs:
   *         modelScore: 0.9
   *         predictProbability: 0.02
   * </pre>
   *
   * @param properties 配置属性
   * @return MockModelInputProvider 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.literule.model",
      name = "mock-enabled",
      havingValue = "true")
  public MockModelInputProvider mockModelInputProvider(LiteRuleProperties properties) {
    LiteRuleProperties.ModelConfig cfg = properties.getModel();
    Map<String, Object> outputs = cfg.getMockOutputs();
    if (outputs != null && !outputs.isEmpty()) {
      log.info("[LiteRule-Model] MockModelInputProvider 已初始化（自定义输出: {}）", outputs);
      return new MockModelInputProvider(MockModelInputProvider.DEFAULT_MODEL_ID, outputs);
    }
    log.info("[LiteRule-Model] MockModelInputProvider 已初始化（默认输出）");
    return new MockModelInputProvider();
  }

  // ------------------------------------------------------------------
  // P0-2 动态事实采集管道（FactProvider SPI）
  // ------------------------------------------------------------------

  /**
   * 事实数据提供者注册表 Bean（P0-2）
   *
   * <p>当 {@code ydsz.literule.fact.enabled=true} 时自动装配，聚合所有 {@link FactProvider} Bean。注册表会自动注入到
   * {@link DefaultRuleEngine}， 使规则引擎在评估前从外部数据源动态采集事实数据。
   *
   * <p>对标滴滴 Newton、字节风控的"动态事实采集"能力：
   *
   * <ul>
   *   <li>规则评估时自动从 DB/Redis/HTTP API 查询业务数据
   *   <li>支持多数据源聚合，按优先级排序执行
   *   <li>超时与异常隔离，单个数据源故障不影响整体评估
   * </ul>
   *
   * @param properties 配置属性
   * @param providersProvider 所有 FactProvider Bean（可选）
   * @return FactProviderRegistry 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "ydsz.literule.fact", name = "enabled", havingValue = "true")
  public FactProviderRegistry factProviderRegistry(
      LiteRuleProperties properties,
      ObjectProvider<FactProvider> providersProvider,
      ApplicationContext applicationContext) {
    LiteRuleProperties.FactConfig cfg = properties.getFact();
    // P1-2: 优先使用 common-thread 统一管理的线程池
    ThreadPoolTaskExecutor threadPool = lookupExecutor(applicationContext, "factProviderExecutor");
    FactProviderRegistry registry;
    if (threadPool != null) {
      registry =
          new FactProviderRegistry(
              cfg.getTimeoutMs(), cfg.isFallbackOnError(), threadPool.getThreadPoolExecutor());
    } else {
      // CHECKSTYLE.OFF: RegexpSinglelineJava - 降级兜底，common-thread 未配置时使用
      registry = new FactProviderRegistry(cfg.getTimeoutMs(), cfg.isFallbackOnError());
      // CHECKSTYLE.ON: RegexpSinglelineJava
      log.warn("[LiteRule-Fact] common-thread bean 'factProviderExecutor' 未配置，使用降级线程池");
    }
    // 注册所有 FactProvider Bean
    List<FactProvider> providers = providersProvider.orderedStream().toList();
    for (FactProvider provider : providers) {
      registry.register(provider);
    }
    log.info(
        "[LiteRule-Fact] 事实数据提供者注册表已初始化 (providers={}, timeoutMs={}, fallbackOnError={})",
        registry.size(),
        cfg.getTimeoutMs(),
        cfg.isFallbackOnError());
    return registry;
  }

  // ------------------------------------------------------------------
  // P1-1 规则与消息通知联动（RuleActionHandler SPI）
  // ------------------------------------------------------------------

  /**
   * 规则动作分发器 Bean（P1-1）
   *
   * <p>当 {@code ydsz.literule.action.enabled=true}（默认 true）时自动装配， 聚合所有 {@link RuleActionHandler}
   * Bean。分发器会自动注入到 {@link DefaultRuleEngine}， 使规则触发后自动执行消息通知等后续动作。
   *
   * @param handlersProvider 所有 RuleActionHandler Bean（可选）
   * @return RuleActionDispatcher 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.literule.action",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RuleActionDispatcher ruleActionDispatcher(
      ObjectProvider<RuleActionHandler> handlersProvider) {
    RuleActionDispatcher dispatcher = new RuleActionDispatcher();
    List<RuleActionHandler> handlers = handlersProvider.orderedStream().toList();
    for (RuleActionHandler handler : handlers) {
      dispatcher.register(handler);
    }
    log.info("[LiteRule-Action] 规则动作分发器已初始化 (handlers={})", dispatcher.size());
    return dispatcher;
  }

  /**
   * 默认告警动作处理器 Bean（P1-1）
   *
   * <p>当 {@code ydsz.literule.action.default-alert-enabled=true}（默认 true）时自动装配， 将规则触发结果转换为 {@link
   * DefaultAlertActionHandler.RuleTriggeredEvent} 并发布。 消费方可通过 {@code @EventListener} 监听此事件， 委托
   * {@code NotifyHelper} 发送通知。
   *
   * @param eventPublisher Spring 事件发布器
   * @return DefaultAlertActionHandler 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.literule.action",
      name = "default-alert-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public DefaultAlertActionHandler defaultAlertActionHandler(
      ApplicationEventPublisher eventPublisher) {
    log.info("[LiteRule-Action] 默认告警动作处理器已初始化");
    return new DefaultAlertActionHandler(eventPublisher);
  }

  // P2-1: 移除 cronjobTriggerActionHandler / workflowTriggerActionHandler Bean 定义。
  // 规则引擎不再直接依赖其他引擎的 Feign 客户端，跨引擎联动由业务系统通过 RuleActionHandler SPI 自行注册。

  // ------------------------------------------------------------------
  // P2-3 高性能优化（评估结果缓存 + 规则分组并行评估）
  // ------------------------------------------------------------------

  /**
   * 规则分组并行评估器（P2-3）
   *
   * <p>当 {@code ydsz.literule.performance.parallel-enabled=true} 时装配， 将候选规则按互斥组分组并行评估。
   *
   * @param properties 配置属性
   * @return ParallelRuleEvaluator 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "ydsz.literule.performance",
      name = "parallel-enabled",
      havingValue = "true")
  public ParallelRuleEvaluator parallelRuleEvaluator(
      LiteRuleProperties properties, ApplicationContext applicationContext) {
    LiteRuleProperties.PerformanceConfig cfg = properties.getPerformance();
    // P1-2: 优先使用 common-thread 统一管理的线程池
    ThreadPoolTaskExecutor threadPool = lookupExecutor(applicationContext, "ruleParallelExecutor");
    ParallelRuleEvaluator evaluator;
    if (threadPool != null) {
      evaluator = new ParallelRuleEvaluator(threadPool);
      log.info("[LiteRule-Performance] 规则并行评估器已初始化（executor=common-thread:ruleParallel)");
    } else {
      // CHECKSTYLE.OFF: RegexpSinglelineJava - 降级兜底，common-thread 未配置时使用
      evaluator = new ParallelRuleEvaluator(cfg.getParallelPoolSize());
      // CHECKSTYLE.ON: RegexpSinglelineJava
      log.warn(
          "[LiteRule-Performance] 规则并行评估器已初始化（poolSize={}, executor=fallback: common-thread bean 'ruleParallelExecutor' 未配置）",
          cfg.getParallelPoolSize());
    }
    return evaluator;
  }

  // ------------------------------------------------------------------
  // P3-1 规则生命周期管理增强（退役检测 + 一键回滚）
  // ------------------------------------------------------------------

  /**
   * 规则生命周期管理服务（P3-1）
   *
   * <p>当存在 {@link RuleConfigProvider} 和 {@link RuleAdminService} 时自动装配。
   * 提供规则退役检测、回滚预览、一键退役等生命周期管理能力。
   *
   * <p>退役检测基于规则执行统计（{@link RuleEngine#getStats()}）， 自动识别休眠规则、高错误率规则、长期停用规则和低影响规则， 生成 {@link
   * com.njydsz.literule.api.RetirementSuggestion} 建议列表。
   *
   * <p>可通过 {@code ydsz.literule.lifecycle.enabled=false} 关闭。
   *
   * @param ruleEngine 规则引擎
   * @param configProvider 规则配置提供者
   * @param ruleAdminService 规则管理服务
   * @param versionRepoProvider 版本仓库（可选，未配置时不支持回滚预览）
   * @param properties 配置属性
   * @return RuleLifecycleService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleConfigProvider.class)
  @ConditionalOnProperty(
      prefix = "ydsz.literule.lifecycle",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public RuleLifecycleService ruleLifecycleService(
      RuleEngine ruleEngine,
      RuleConfigProvider configProvider,
      RuleAdminService ruleAdminService,
      ObjectProvider<RuleVersionRepository> versionRepoProvider,
      LiteRuleProperties properties) {
    RuleLifecycleService service =
        new RuleLifecycleService(
            ruleEngine, configProvider, ruleAdminService, versionRepoProvider.getIfAvailable());
    service.configure(properties.getLifecycle());
    log.info(
        "[LiteRule-Lifecycle] 规则生命周期管理服务已初始化（dormantMin={}, errorRateThreshold={}, staleDays={}, lowImpactRate={}）",
        properties.getLifecycle().getDormantMinEvaluations(),
        properties.getLifecycle().getHighErrorRateThreshold(),
        properties.getLifecycle().getStaleDisabledDays(),
        properties.getLifecycle().getLowImpactTriggerRate());
    return service;
  }

  // ------------------------------------------------------------------
  // P3-4 执行回放服务
  // ------------------------------------------------------------------

  /**
   * 执行回放服务（P3-4）
   *
   * <p>当存在 {@link RuleAdminService} 时自动装配， 提供基于历史执行轨迹的事实快照重新评估规则的能力。
   *
   * @param ruleAdminService 规则管理服务
   * @param traceRecorderProvider 轨迹记录器（可选）
   * @param versionRepoProvider 版本仓库（可选，支持版本回放）
   * @param evaluator 表达式求值器
   * @return ExecutionReplayService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleAdminService.class)
  public ExecutionReplayService executionReplayService(
      RuleAdminService ruleAdminService,
      ObjectProvider<TraceRecorder> traceRecorderProvider,
      ObjectProvider<RuleVersionRepository> versionRepoProvider,
      ExpressionEngine evaluator) {
    ExecutionReplayService service =
        new ExecutionReplayService(
            ruleAdminService,
            traceRecorderProvider.getIfAvailable(),
            versionRepoProvider.getIfAvailable(),
            evaluator);
    log.info(
        "[LiteRule-Replay] 执行回放服务已初始化（traceRecorder={}, versionRepo={}）",
        traceRecorderProvider.getIfAvailable() != null,
        versionRepoProvider.getIfAvailable() != null);
    return service;
  }

  // ------------------------------------------------------------------
  // P3-5 审计日志服务
  // ------------------------------------------------------------------

  /**
   * 规则审计日志服务（P3-5）
   *
   * <p>当存在 {@link RuleAdminService} 时自动装配， 记录规则全生命周期操作的审计日志。 默认使用内存存储，可通过 {@link
   * com.njydsz.literule.server.audit.RuleAuditLogService.AuditLogStore} SPI 提供持久化实现。
   *
   * @param auditLogStoreProvider 审计日志存储（可选，为空使用内存存储）
   * @return RuleAuditLogService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleAdminService.class)
  public RuleAuditLogService ruleAuditLogService(
      ObjectProvider<RuleAuditLogService.AuditLogStore> auditLogStoreProvider) {
    RuleAuditLogService.AuditLogStore store = auditLogStoreProvider.getIfAvailable();
    RuleAuditLogService service = new RuleAuditLogService(store);
    log.info(
        "[LiteRule-Audit] 规则审计日志服务已初始化（store={}）",
        store != null ? store.getClass().getSimpleName() : "InMemory");
    return service;
  }

  // ------------------------------------------------------------------
  // P3-3 SDK Bean 注册（Spring Boot 场景下可通过 @Autowired 获取 LiteRuleSdk）
  // ------------------------------------------------------------------

  /**
   * LiteRule SDK Bean（P3-3）
   *
   * <p>当 {@code RuleEngine} 和 {@code ExpressionEngine} Bean 存在时自动装配， 使业务方可在 Spring Boot 场景下通过
   * {@code @Autowired} 注入 {@link LiteRuleSdk}， 无需手动调用 {@code LiteRuleSdk.builder()}。
   *
   * <p>可通过 {@code ydsz.literule.sdk.enabled=false} 关闭。
   *
   * @param ruleEngine 规则引擎
   * @param evaluator 表达式求值器
   * @param properties 配置属性
   * @return LiteRuleSdk 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean(LiteRuleSdk.class)
  @ConditionalOnProperty(
      prefix = "ydsz.literule.sdk",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LiteRuleSdk liteRuleSdk(
      RuleEngine ruleEngine, ExpressionEngine evaluator, LiteRuleProperties properties) {
    LiteRuleSdk sdk =
        new LiteRuleSdk(
            ruleEngine, evaluator, properties.getDefaultTenantId(), properties.getEnvironment());
    log.info("[LiteRule-SDK] LiteRuleSdk 已初始化（environment={}）", properties.getEnvironment());
    return sdk;
  }

  // ------------------------------------------------------------------
  // P1-2 定时维护任务
  // ------------------------------------------------------------------

  /**
   * 定时维护任务（P1-2）
   *
   * <p>每 60 秒执行一次 CEP 过期事件清理，防止事件队列无限增长。 依赖 @EnableScheduling 注解（已在类级别添加）。
   *
   * @param cepEngine CEP 引擎（可选注入，未启用时跳过）
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(CEPEngine.class)
  public LiteRuleMaintenanceTask liteRuleMaintenanceTask(CEPEngine cepEngine) {
    log.info("[LiteRule-Maintenance] 定时维护任务已初始化");
    return new LiteRuleMaintenanceTask(cepEngine);
  }

  /**
   * 定时维护任务内部类
   *
   * <p>分离为独立类避免 AutoConfiguration 直接持有 @Scheduled 方法 导致的条件装配复杂化。
   *
   * @since 1.0.0
   */
  public static class LiteRuleMaintenanceTask {

    private final CEPEngine cepEngine;

    public LiteRuleMaintenanceTask(CEPEngine cepEngine) {
      this.cepEngine = cepEngine;
    }

    /** CEP 过期事件清理（每 60 秒执行一次） */
    @Scheduled(fixedDelay = 60_000)
    @DistributedScheduled(lockKey = "literule:cep-cleanup", leaseTime = 120)
    public void cleanupCepExpiredEvents() {
      try {
        cepEngine.cleanupExpiredEvents();
      } catch (Exception e) {
        log.warn("[LiteRule-Maintenance] CEP 过期事件清理失败: {}", e.getMessage());
      }
    }
  }

  // ------------------------------------------------------------------
  // P0-T2 规则压测服务（@Bean 注册，替代 @Service）
  // ------------------------------------------------------------------

  /**
   * 规则压测服务 Bean（P0-T2）
   *
   * <p>当存在 {@link RuleAdminService} 时自动装配，提供规则引擎并发压测能力。 从 @Service 改为 @Bean 注册，与项目其他模块规范一致。
   *
   * @param ruleAdminService 规则管理服务
   * @return RuleStressTestService 实例
   * @since 1.0.0
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(RuleAdminService.class)
  public RuleStressTestService ruleStressTestService(RuleAdminService ruleAdminService) {
    log.info("[LiteRule-Benchmark] 规则压测服务已初始化");
    return new RuleStressTestService(ruleAdminService);
  }
}
