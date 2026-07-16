package com.njydsz.pmis.literule.server.config;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.njydsz.pmis.cronjob.api.client.CronjobServiceClient;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.domain.model.MockModelInputProvider;
import com.njydsz.pmis.literule.domain.model.ModelInputProvider;
import com.njydsz.pmis.literule.domain.model.ModelInputRegistry;
import com.njydsz.pmis.literule.server.approval.ApprovalPermissionChecker;
import com.njydsz.pmis.literule.server.approval.ApprovalRecordRepository;
import com.njydsz.pmis.literule.server.approval.RuleApprovalService;
import com.njydsz.pmis.literule.server.approval.RuleApprovalWorkflowBridge;
import com.njydsz.pmis.literule.server.audit.RuleAuditLogService;
import com.njydsz.pmis.literule.server.cache.CachingRuleConfigProvider;
import com.njydsz.pmis.literule.server.cep.CEPEngine;
import com.njydsz.pmis.literule.server.core.AsyncTraceRecorder;
import com.njydsz.pmis.literule.server.core.BreakpointHook;
import com.njydsz.pmis.literule.server.core.DefaultBreakpointHook;
import com.njydsz.pmis.literule.server.core.DefaultRuleEngine;
import com.njydsz.pmis.literule.server.core.EvaluationResultCache;
import com.njydsz.pmis.literule.server.core.MicrometerRuleMetrics;
import com.njydsz.pmis.literule.server.core.ParallelRuleEvaluator;
import com.njydsz.pmis.literule.server.core.RuleCanaryRouter;
import com.njydsz.pmis.literule.server.core.RuleCircuitBreaker;
import com.njydsz.pmis.literule.server.core.RuleDocumentationService;
import com.njydsz.pmis.literule.server.core.RuleEffectivenessService;
import com.njydsz.pmis.literule.server.core.RuleLifecycleService;
import com.njydsz.pmis.literule.server.core.RuleMetrics;
import com.njydsz.pmis.literule.server.core.RuleTimeoutExecutor;
import com.njydsz.pmis.literule.server.expr.EmptyVariableRegistry;
import com.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.server.expr.ExpressionValidationService;
import com.njydsz.pmis.literule.server.expr.VariableRegistry;
import com.njydsz.pmis.literule.server.expr.liteexpr.LiteExprEvaluator;
import com.njydsz.pmis.literule.server.replay.ExecutionReplayService;
import com.njydsz.pmis.literule.server.spi.CronjobTriggerActionHandler;
import com.njydsz.pmis.literule.server.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.server.spi.DecisionTreeConfigProvider;
import com.njydsz.pmis.literule.server.spi.DefaultAlertActionHandler;
import com.njydsz.pmis.literule.server.spi.FactProvider;
import com.njydsz.pmis.literule.server.spi.FactProviderRegistry;
import com.njydsz.pmis.literule.server.spi.FileRuleSource;
import com.njydsz.pmis.literule.server.spi.RuleActionDispatcher;
import com.njydsz.pmis.literule.server.spi.RuleActionHandler;
import com.njydsz.pmis.literule.server.spi.RuleConfigBroadcaster;
import com.njydsz.pmis.literule.server.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.server.spi.RuleVersionRepository;
import com.njydsz.pmis.literule.server.spi.ScorecardConfigProvider;
import com.njydsz.pmis.literule.server.spi.ScriptConfigProvider;
import com.njydsz.pmis.literule.server.spi.TraceRecorder;
import com.njydsz.pmis.literule.server.spi.WorkflowTriggerActionHandler;
import com.njydsz.pmis.workflow.api.client.WorkflowServiceClient;

import lombok.extern.slf4j.Slf4j;

/**
 * LiteRule 自动配置
 *
 * <p>自动注册核心组件：表达式求值器、规则引擎、规则管理服务。
 * 当 classpath 中存在 RuleConfigProvider 实现时，自动启用动态规则加载和热刷新。
 *
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LiteRuleProperties.class)
@ConditionalOnProperty(prefix = "pmis.literule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiteRuleAutoConfiguration {

    /**
     * 表达式求值器
     *
     * <p>2.1.0 起仅保留自研 {@link LiteExprEvaluator}，零外部依赖、AST 原生追踪/沙箱/变量提取。
     *
     * @param properties 配置属性
     * @return ExpressionEvaluator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionEvaluator expressionEvaluator(LiteRuleProperties properties) {
        log.info("[LiteRule] LiteExpr 自研表达式求值器已初始化（sandbox={}）", properties.isSandboxEnabled());
        return new LiteExprEvaluator(properties.isSandboxEnabled());
    }

    /**
     * 规则引擎
     *
     * <p>1.4.0 起：
     * <ul>
     *   <li>当 traceEnabled=true 时自动装配 {@link AsyncTraceRecorder}，
     *       若消费方提供了自定义 {@link TraceRecorder} Bean 则作为持久化委托</li>
     *   <li>当 ruleTimeoutMs > 0 时启用 {@link RuleTimeoutExecutor}</li>
     *   <li>当 circuitBreakerMinEvaluations > 0 时启用 {@link RuleCircuitBreaker}（P2-14: openStateMs 由 {@code pmis.literule.circuit-breaker-open-state-ms} 配置）</li>
     *   <li>当 MeterRegistry 可用时启用 {@link MicrometerRuleMetrics}</li>
     *   <li>当 canaryEnabled=true 时启用 {@link RuleCanaryRouter}</li>
     * </ul>
     *
     * @param properties         配置属性
     * @param traceDelegateProvider 持久化委托提供者（可选）
     * @param meterRegistryProvider Micrometer 注册器（可选）
     * @return DefaultRuleEngine 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RuleEngine ruleEngine(LiteRuleProperties properties,
                                  ObjectProvider<TraceRecorder> traceDelegateProvider,
                                  ObjectProvider<ExpressionEvaluator> evaluatorProvider,
                                  ObjectProvider<BreakpointHook> breakpointHookProvider,
                                  ObjectProvider<ModelInputRegistry> modelRegistryProvider,
                                  ObjectProvider<FactProviderRegistry> factRegistryProvider,
                                  ObjectProvider<RuleActionDispatcher> actionDispatcherProvider,
                                  ObjectProvider<ParallelRuleEvaluator> parallelEvaluatorProvider,
                                  ApplicationContext applicationContext) {
        DefaultRuleEngine engine = new DefaultRuleEngine();
        engine.setStatsEnabled(properties.isStatsEnabled());

        // 断点调试 Hook（P2-3）：可选注入，仅当应用层提供实现时生效
        BreakpointHook bpHook = breakpointHookProvider.getIfAvailable();
        if (bpHook != null) {
            engine.setBreakpointHook(bpHook);
        }

        // P0-2 动态事实采集：可选注入事实提供者注册表
        FactProviderRegistry factRegistry = factRegistryProvider.getIfAvailable();
        if (factRegistry != null) {
            engine.setFactProviderRegistry(factRegistry);
        }

        // P1-1 规则与消息通知联动：可选注入动作分发器
        // actionDispatcherProvider 通过下方 Bean 自动装配
        RuleActionDispatcher actionDispatcher = actionDispatcherProvider.getIfAvailable();
        if (actionDispatcher != null) {
            engine.setActionDispatcher(actionDispatcher);
        }

        // P2-2 并行评估：可选注入并行评估器（pmis.literule.performance.parallel-enabled=true 时生效）
        ParallelRuleEvaluator parallelEvaluator = parallelEvaluatorProvider.getIfAvailable();
        if (parallelEvaluator != null) {
            engine.setParallelEvaluator(parallelEvaluator);
            engine.setParallelThreshold(properties.getPerformance().getParallelThreshold());
        }

        // P2-4 慢规则告警：阈值 > 0 时启用
        long slowRuleThresholdMs = properties.getPerformance().getSlowRuleThresholdMs();
        if (slowRuleThresholdMs > 0) {
            engine.setSlowRuleThresholdMs(slowRuleThresholdMs);
        }

        // P3-1 规则+模型融合：可选注入模型注册表
        ModelInputRegistry modelRegistry = modelRegistryProvider.getIfAvailable();
        if (modelRegistry != null) {
            engine.setModelInputRegistry(modelRegistry);
        }

        if (properties.isTraceEnabled()) {
            AsyncTraceRecorder asyncRecorder = new AsyncTraceRecorder(
                    properties.getTraceQueueCapacity(),
                    properties.getTraceBatchSize(),
                    properties.getTraceFlushIntervalMs());
            TraceRecorder delegate = traceDelegateProvider.getIfAvailable();
            if (delegate != null && !(delegate instanceof AsyncTraceRecorder)) {
                asyncRecorder.setDelegate(delegate);
                log.info("[LiteRule] Trace 持久化委托已注入: {}", delegate.getClass().getSimpleName());
            }
            engine.setTraceRecorder(asyncRecorder);
            log.info("[LiteRule] 异步 Trace 记录已启用 (queueCapacity={}, batchSize={}, flushMs={}, delegate={})",
                    properties.getTraceQueueCapacity(), properties.getTraceBatchSize(),
                    properties.getTraceFlushIntervalMs(), delegate != null);
        }

        if (properties.getRuleTimeoutMs() > 0) {
            int poolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
            RuleTimeoutExecutor timeoutExecutor = new RuleTimeoutExecutor(properties.getRuleTimeoutMs(), poolSize);
            engine.setTimeoutExecutor(timeoutExecutor);
            log.info("[LiteRule] 单规则超时控制已启用 (timeoutMs={}, poolSize={})",
                    properties.getRuleTimeoutMs(), poolSize);
        }

        if (properties.getCircuitBreakerMinEvaluations() > 0) {
            // P2-14: openStateMs 从 properties 读取，消除硬编码 30_000L
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(
                    properties.getCircuitBreakerErrorRate(),
                    properties.getCircuitBreakerMinEvaluations(),
                    properties.getCircuitBreakerOpenStateMs());
            engine.setCircuitBreaker(breaker);
            log.info("[LiteRule] 规则熔断器已启用 (errorRateThreshold={}, minEvaluations={}, openStateMs={})",
                    properties.getCircuitBreakerErrorRate(), properties.getCircuitBreakerMinEvaluations(),
                    properties.getCircuitBreakerOpenStateMs());
        }

        if (properties.isCanaryEnabled()) {
            ExpressionEvaluator evaluator = evaluatorProvider.getIfAvailable();
            if (evaluator == null) {
                evaluator = expressionEvaluator(properties);
            }
            RuleCanaryRouter canaryRouter = new RuleCanaryRouter(evaluator);
            engine.setCanaryRouter(canaryRouter);
            engine.setCanaryEnabled(true);
            log.info("[LiteRule] 规则灰度路由已启用");
        }

        // Micrometer 桥接（仅当 classpath 存在 MeterRegistry 时启用）
        bindMicrometerIfAvailable(engine, applicationContext);

        log.info("[LiteRule] 默认规则引擎已初始化（statsEnabled={}, traceEnabled={}, timeoutMs={}, breaker={}, metrics={}, canary={}, breakpoint={}, model={}, slowRuleThreshold={}ms）",
                properties.isStatsEnabled(), properties.isTraceEnabled(),
                properties.getRuleTimeoutMs(), properties.getCircuitBreakerMinEvaluations() > 0,
                engine.getMetrics() != null, engine.getCanaryRouter() != null,
                engine.getBreakpointHook() != null, engine.getModelInputRegistry() != null,
                properties.getPerformance().getSlowRuleThresholdMs());
        return engine;
    }

    /**
     * 当 classpath 存在 MeterRegistry 时桥接到 Micrometer
     *
     * <p>使用反射式检测避免对 MeterRegistry 类的硬依赖，使得 literule 在缺少 micrometer 依赖的环境下仍能工作。
     */
    private void bindMicrometerIfAvailable(DefaultRuleEngine engine, ApplicationContext ctx) {
        Class<?> meterRegistryClass;
        try {
            meterRegistryClass = Class.forName("io.micrometer.core.instrument.MeterRegistry", false,
                    getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            log.debug("[LiteRule] Micrometer 不在 classpath，跳过 Prometheus 指标桥接");
            return;
        }
        Map<String, ?> beans = ctx.getBeansOfType(meterRegistryClass);
        if (beans.isEmpty()) {
            log.debug("[LiteRule] 未找到 MeterRegistry Bean，跳过 Prometheus 指标桥接");
            return;
        }
        Object registry = beans.values().iterator().next();
        try {
            Class<?> metricsClass = Class.forName(
                    "com.njydsz.pmis.literule.server.core.MicrometerRuleMetrics", true,
                    getClass().getClassLoader());
            Constructor<?> ctor = metricsClass.getConstructor(meterRegistryClass);
            RuleMetrics metrics = (RuleMetrics) ctor.newInstance(registry);
            engine.setMetrics(metrics);
            log.info("[LiteRule] Prometheus 监控指标已启用 (registry={})",
                    registry.getClass().getSimpleName());
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
    public ABTestService abTestService(ExpressionEvaluator evaluator) {
        log.info("[LiteRule] A/B 测试服务已初始化");
        return new ABTestService(evaluator);
    }

    /**
     * 表达式校验服务（1.4.0 起支持）
     *
     * <p>面向前端表达式编辑器的校验 API，提供结构化的错误信息。
     * 当 classpath 中存在 {@link VariableRegistry} Bean 时，
     * 启用 UNDEFINED_VARIABLE 校验；否则使用 {@link EmptyVariableRegistry} 跳过。
     *
     * @param evaluator 表达式求值器
     * @param registryProvider 变量注册表（可选）
     * @return ExpressionValidationService 实例
     * @since 1.4.0
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionValidationService expressionValidationService(
            ExpressionEvaluator evaluator,
            ObjectProvider<VariableRegistry> registryProvider) {
        VariableRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            registry = new EmptyVariableRegistry();
            log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验未启用）");
        } else {
            log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验已启用，已注册 {} 个变量）",
                    registry.listAll().size());
        }
        return new ExpressionValidationService(evaluator, registry);
    }

    /**
     * 规则热加载管理器（当存在 RuleConfigProvider 时生效）
     *
     * <p>1.4.0 起支持以下可选 SPI：决策表/评分卡/决策树/脚本规则的动态加载。
     *
     * @param ruleEngine       规则引擎
     * @param evaluator        表达式求值器
     * @param configProvider   规则配置提供者
     * @param dtConfigProvider 决策表配置提供者（可选）
     * @param scConfigProvider 评分卡配置提供者（可选）
     * @param trConfigProvider 决策树配置提供者（可选）
     * @param scriptConfigProvider 脚本规则配置提供者（可选）
     * @param properties       配置属性
     * @return RuleHotReloader 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuleConfigProvider.class)
    public RuleHotReloader ruleHotReloader(RuleEngine ruleEngine,
                                            ExpressionEvaluator evaluator,
                                            RuleConfigProvider configProvider,
                                            ObjectProvider<DecisionTableConfigProvider> dtConfigProvider,
                                            ObjectProvider<ScorecardConfigProvider> scConfigProvider,
                                            ObjectProvider<DecisionTreeConfigProvider> trConfigProvider,
                                            ObjectProvider<ScriptConfigProvider> scriptConfigProvider,
                                            LiteRuleProperties properties) {
        RuleHotReloader reloader = new RuleHotReloader(ruleEngine, evaluator, configProvider, properties);

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

        log.info("[LiteRule] 规则热加载管理器已初始化（hotReload={}, decisionTable={}, scorecard={}, decisionTree={}, script={}）",
                properties.isHotReloadEnabled(), dt != null, sc != null, tr != null, script != null);
        return reloader;
    }

    /**
     * 决策表管理服务（当存在 DecisionTableConfigProvider 时生效）
     *
     * @param ruleEngine        规则引擎
     * @param dtConfigProvider  决策表配置提供者
     * @param broadcasterProvider 广播器（可选）
     * @param eventPublisher    事件发布器
     * @return DecisionTableAdminService 实例
     * @since 1.4.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DecisionTableConfigProvider.class)
    public DecisionTableAdminService decisionTableAdminService(RuleEngine ruleEngine,
                                                                DecisionTableConfigProvider dtConfigProvider,
                                                                ObjectProvider<RuleConfigBroadcaster> broadcasterProvider,
                                                                ApplicationEventPublisher eventPublisher) {
        DecisionTableAdminService service = new DecisionTableAdminService(ruleEngine, dtConfigProvider, eventPublisher);
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
     * @param ruleEngine     规则引擎
     * @param evaluator      表达式求值器
     * @param configProvider 规则配置提供者
     * @param versionRepo    版本仓库（可选）
     * @param eventPublisher 事件发布器
     * @return RuleAdminService 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuleConfigProvider.class)
    public RuleAdminService ruleAdminService(RuleEngine ruleEngine,
                                              ExpressionEvaluator evaluator,
                                              RuleConfigProvider configProvider,
                                              ObjectProvider<RuleVersionRepository> versionRepoProvider,
                                              ObjectProvider<RuleConfigBroadcaster> broadcasterProvider,
                                              ApplicationEventPublisher eventPublisher,
                                              LiteRuleProperties properties) {
        RuleAdminService service = new RuleAdminService(ruleEngine, evaluator, configProvider,
                versionRepoProvider.getIfAvailable(), eventPublisher);
        service.setDryRunEnabled(properties.isDryRunEnabled());
        RuleConfigBroadcaster broadcaster = broadcasterProvider.getIfAvailable();
        if (broadcaster != null) {
            service.setBroadcaster(broadcaster);
            log.info("[LiteRule] 分布式规则广播已启用");
        }
        // 冲突检测（1.4.0 起支持，仅在启用时装配检测器）
        if (properties.isConflictDetectionEnabled()) {
            RuleConflictDetector conflictDetector = new RuleConflictDetector(configProvider);
            service.setConflictDetector(conflictDetector);
            service.setConflictDetectionEnabled(true);
            service.setConflictDetectionBlockOnError(properties.isConflictDetectionBlockOnError());
            log.info("[LiteRule] 规则冲突检测已启用（blockOnError={}）",
                    properties.isConflictDetectionBlockOnError());
        } else {
            // 显式关闭：即便上层手动注入检测器，也不生效
            service.setConflictDetectionEnabled(false);
        }
        log.info("[LiteRule] 规则管理服务已初始化（dryRun={}, broadcast={}, conflictDetection={}）",
                properties.isDryRunEnabled(), broadcaster != null, properties.isConflictDetectionEnabled());
        return service;
    }

    /**
     * 规则审批流服务（P1-3 多级审批流）
     *
     * <p>当存在 {@link RuleConfigProvider} 时自动装配。默认注册 2 级审批流
     * （default-2level），消费方可通过 {@link RuleApprovalService#registerFlow}
     * 注册自定义审批流。审批记录默认内存存储，可通过
     * {@link ApprovalRecordRepository} SPI 提供持久化实现；权限校验可通过
     * {@link ApprovalPermissionChecker} SPI 委托给消费方。
     *
     * @param configProvider       规则配置提供者
     * @param recordRepoProvider   审批记录持久化仓库（可选）
     * @param permissionCheckerProvider 权限检查器（可选）
     * @return RuleApprovalService 实例
     * @since 1.7.0
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
        log.info("[LiteRule-Approval] 规则审批流服务已初始化（recordRepository={}, permissionChecker={}, workflowBridge={}）",
                recordRepo != null, checker != null, workflowBridge != null);
        return service;
    }

    // ------------------------------------------------------------------
    // P1-1 多级缓存（Caffeine + Redis）
    // ------------------------------------------------------------------

    /**
     * 多级缓存 RuleConfigProvider 装饰器（P1-1）
     *
     * <p>当 classpath 存在 {@link RuleConfigProvider} 实现且
     * {@code pmis.literule.cache.enabled=true}（默认 true）时，
     * 自动装饰委托 Provider 为 {@link CachingRuleConfigProvider}，
     * 启用 Caffeine（L1 本地）+ Redis（L2 分布式）两级缓存，减少 DB 压力。
     *
     * <p>L2 启用条件：
     * <ul>
     *   <li>classpath 存在 {@code RedissonClient}（通过 {@code ObjectProvider} 安全获取）</li>
     *   <li>{@code pmis.literule.cache.l2-enabled=true}（默认 true）</li>
     * </ul>
     * 任一不满足则仅启用 L1。
     *
     * <p>使用 {@link Primary} 确保其他组件
     * （{@link RuleHotReloader} / {@link RuleAdminService}）注入的是缓存装饰器而非原始 Provider。
     *
     * @param providers           所有 RuleConfigProvider Bean（过滤掉 CachingRuleConfigProvider 自身）
     * @param redissonClientProvider Redisson 客户端（可选，不存在时降级为仅 L1）
     * @param properties          配置属性
     * @return CachingRuleConfigProvider 实例
     * @since 1.6.0
     */
    @Bean
    @ConditionalOnMissingBean(CachingRuleConfigProvider.class)
    @ConditionalOnBean(RuleConfigProvider.class)
    @ConditionalOnProperty(prefix = "pmis.literule.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
    @Primary
    public CachingRuleConfigProvider cachingRuleConfigProvider(
            List<RuleConfigProvider> providers,
            ObjectProvider<RedissonClient> redissonClientProvider,
            LiteRuleProperties properties) {
        // 过滤掉 CachingRuleConfigProvider 自身（避免循环装饰），取第一个作为委托
        RuleConfigProvider delegate = providers.stream()
                .filter(p -> !(p instanceof CachingRuleConfigProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到可装饰的 RuleConfigProvider 委托实现"));
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        log.info("[LiteRule-Cache] 多级缓存 RuleConfigProvider 已初始化 (delegate={}, L2={})",
                delegate.getClass().getSimpleName(), redissonClient != null);
        return new CachingRuleConfigProvider(delegate, redissonClient, properties);
    }

    // ------------------------------------------------------------------
    // CEP 复杂事件处理引擎（P0-2）
    // ------------------------------------------------------------------

    /**
     * CEP 引擎 Bean
     *
     * <p>默认装配为单例，业务侧通过 {@link CEPEngine#feed}
     * 投递事件、通过 {@link CEPEngine#registerPattern}
     * 注册模式。命中模式后通过 Listener 回调触发关联规则。
     *
     * <p>可通过 {@code pmis.literule.cep.enabled=false} 关闭。
     *
     * @return CEPEngine 实例
     * @since 1.5.1
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "pmis.literule.cep", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CEPEngine cepEngine() {
        CEPEngine engine = new CEPEngine();
        log.info("[LiteRule-CEP] 复杂事件处理引擎已初始化");
        return engine;
    }

    // ------------------------------------------------------------------
    // 断点调试器（P0-3 落地）
    // ------------------------------------------------------------------

    /**
     * 默认断点调试器 Bean
     *
     * <p>装配后自动注入到 {@link DefaultRuleEngine}，
     * 业务侧可通过 {@code /execution/rules/breakpoints} REST API 管理断点与下发调试指令。
     *
     * <p>可通过 {@code pmis.literule.debug.enabled=false} 关闭。
     *
     * @return DefaultBreakpointHook 实例
     * @since 1.5.1
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "pmis.literule.debug", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DefaultBreakpointHook defaultBreakpointHook(LiteRuleProperties properties) {
        DefaultBreakpointHook hook =
                new DefaultBreakpointHook();
        log.info("[LiteRule-Debug] 断点调试器已初始化（suspendTimeout={}s）",
                60);
        return hook;
    }

    // ------------------------------------------------------------------
    // 声明式规则注解（P2-10）
    // ------------------------------------------------------------------

    /**
     * 声明式规则注册器（P2-10）
     *
     * <p>容器刷新完成后扫描 {@code @LiteRule}（标注在 Rule Bean 上）与
     * {@code @RuleDefinitionMeta}（纯声明式表达式规则）并自动注册到引擎。
     * 通过 {@code pmis.literule.annotation-scan-base-ppackages} 指定扫描基包（逗号分隔）。
     *
     * @return LiteRuleAnnotationRegistrar 实例
     * @since 1.5.2
     */
    @Bean
    @ConditionalOnMissingBean
    public LiteRuleAnnotationRegistrar liteRuleAnnotationRegistrar(RuleEngine ruleEngine,
                                                                   ExpressionEvaluator evaluator,
                                                                   ApplicationContext applicationContext,
                                                                   LiteRuleProperties properties) {
        LiteRuleAnnotationRegistrar registrar =
                new LiteRuleAnnotationRegistrar(ruleEngine, evaluator, applicationContext, properties);
        log.info("[LiteRule-Annotation] 声明式规则注册器已初始化（scanBasePpackages={}）",
                properties.getAnnotationScanBasePpackages());
        return registrar;
    }

    // ------------------------------------------------------------------
    // P2-3 DSL YAML/JSON 规则文件加载（FileRuleSource）
    // ------------------------------------------------------------------

    /**
     * 文件规则数据源 Bean（P2-3）
     *
     * <p>当 {@code pmis.literule.file-source.enabled=true} 时自动装配
     * {@link FileRuleSource}，从 classpath 或文件系统
     * 加载 YAML/JSON 规则文件。加载后可配合 {@link RuleHotReloader} 注册到引擎。
     *
     * <p>Bean 初始化时调用 {@code init()}，销毁时调用 {@code destroy()} 释放 WatchService。
     *
     * @param properties 配置属性
     * @return FileRuleSource 实例
     * @since 1.7.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "pmis.literule.file-source", name = "enabled", havingValue = "true")
    public FileRuleSource fileRuleSource(LiteRuleProperties properties) {
        LiteRuleProperties.FileSourceConfig cfg = properties.getFileSource();
        FileRuleSource source =
                new FileRuleSource(cfg.getLocation(), cfg.isWatch());
        try {
            source.init();
            log.info("[LiteRule-FileSource] 文件规则源已初始化（location={}, watch={}, rules={}）",
                    cfg.getLocation(), cfg.isWatch(), source.loadAllRules().size());
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
     * <p>当 {@code pmis.literule.model.enabled=true} 时自动装配，聚合所有
     * {@link ModelInputProvider} Bean（包括可选的 {@link MockModelInputProvider}）。
     * 注册表会自动注入到 {@link DefaultRuleEngine}，使规则表达式可通过
     * {@code model.<field>} 引用模型输出（如 {@code model.riskScore > 0.8}）。
     *
     * <p>对标滴滴 Newton、字节风控的"规则+模型融合"能力：
     * <ul>
     *   <li>规则兜底模型异常：模型不可用时降级为纯规则评估</li>
     *   <li>模型输出触发规则：模型输出作为规则条件输入</li>
     * </ul>
     *
     * @param properties         配置属性
     * @param providersProvider  所有 ModelInputProvider Bean（可选，含 Mock）
     * @return ModelInputRegistry 实例
     * @since 1.8.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.model", name = "enabled", havingValue = "true")
    public ModelInputRegistry modelInputRegistry(LiteRuleProperties properties,
                                                  ObjectProvider<ModelInputProvider> providersProvider) {
        LiteRuleProperties.ModelConfig cfg = properties.getModel();
        ModelInputRegistry registry = new ModelInputRegistry(cfg.getTimeoutMs(), cfg.isFallbackOnError());
        // 注册所有 ModelInputProvider Bean（包括 MockModelInputProvider）
        List<ModelInputProvider> providers = providersProvider.orderedStream().toList();
        for (ModelInputProvider provider : providers) {
            registry.register(provider);
        }
        log.info("[LiteRule-Model] 模型输入注册表已初始化 (providers={}, timeoutMs={}, fallbackOnError={})",
                registry.size(), cfg.getTimeoutMs(), cfg.isFallbackOnError());
        return registry;
    }

    /**
     * Mock 模型输入提供者 Bean（P3-1）
     *
     * <p>当 {@code pmis.literule.model.mock-enabled=true} 时自动装配，返回配置的
     * 模拟模型输出，便于开发/测试环境验证规则+模型融合能力，无需依赖真实模型服务。
     *
     * <p>输出可通过 {@code pmis.literule.model.mock-outputs} 配置自定义：
     * <pre>
     * pmis:
     *   literule:
     *     model:
     *       enabled: true
     *       mock-enabled: true
     *       mock-outputs:
     *         riskScore: 0.9
     *         fraudProbability: 0.02
     * </pre>
     *
     * @param properties 配置属性
     * @return MockModelInputProvider 实例
     * @since 1.8.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.model", name = "mock-enabled", havingValue = "true")
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
     * <p>当 {@code pmis.literule.fact.enabled=true} 时自动装配，聚合所有
     * {@link FactProvider} Bean。注册表会自动注入到 {@link DefaultRuleEngine}，
     * 使规则引擎在评估前从外部数据源动态采集事实数据。
     *
     * <p>对标滴滴 Newton、字节风控的"动态事实采集"能力：
     * <ul>
     *   <li>规则评估时自动从 DB/Redis/HTTP API 查询业务数据</li>
     *   <li>支持多数据源聚合，按优先级排序执行</li>
     *   <li>超时与异常隔离，单个数据源故障不影响整体评估</li>
     * </ul>
     *
     * @param properties        配置属性
     * @param providersProvider 所有 FactProvider Bean（可选）
     * @return FactProviderRegistry 实例
     * @since 2.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.fact", name = "enabled", havingValue = "true")
    public FactProviderRegistry factProviderRegistry(LiteRuleProperties properties,
                                                       ObjectProvider<FactProvider> providersProvider) {
        LiteRuleProperties.FactConfig cfg = properties.getFact();
        FactProviderRegistry registry = new FactProviderRegistry(cfg.getTimeoutMs(), cfg.isFallbackOnError());
        // 注册所有 FactProvider Bean
        List<FactProvider> providers = providersProvider.orderedStream().toList();
        for (FactProvider provider : providers) {
            registry.register(provider);
        }
        log.info("[LiteRule-Fact] 事实数据提供者注册表已初始化 (providers={}, timeoutMs={}, fallbackOnError={})",
                registry.size(), cfg.getTimeoutMs(), cfg.isFallbackOnError());
        return registry;
    }

    // ------------------------------------------------------------------
    // P1-1 规则与消息通知联动（RuleActionHandler SPI）
    // ------------------------------------------------------------------

    /**
     * 规则动作分发器 Bean（P1-1）
     *
     * <p>当 {@code pmis.literule.action.enabled=true}（默认 true）时自动装配，
     * 聚合所有 {@link RuleActionHandler} Bean。分发器会自动注入到 {@link DefaultRuleEngine}，
     * 使规则触发后自动执行消息通知等后续动作。
     *
     * @param handlersProvider 所有 RuleActionHandler Bean（可选）
     * @return RuleActionDispatcher 实例
     * @since 2.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.action", name = "enabled",
            havingValue = "true", matchIfMissing = true)
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
     * <p>当 {@code pmis.literule.action.default-alert-enabled=true}（默认 true）时自动装配，
     * 将规则触发结果转换为 {@link DefaultAlertActionHandler.RuleTriggeredEvent} 并发布。
     * 消费方可通过 {@code @EventListener} 监听此事件，转换为 {@code UnifiedAlertEvent}
     * 由 common 模块的 {@code UnifiedAlertDispatcher} 统一发送通知。
     *
     * @param eventPublisher Spring 事件发布器
     * @return DefaultAlertActionHandler 实例
     * @since 2.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.action", name = "default-alert-enabled",
            havingValue = "true", matchIfMissing = true)
    public DefaultAlertActionHandler defaultAlertActionHandler(
            ApplicationEventPublisher eventPublisher) {
        log.info("[LiteRule-Action] 默认告警动作处理器已初始化");
        return new DefaultAlertActionHandler(eventPublisher);
    }

    /**
     * 定时任务触发动作处理器 Bean（P1-2 规则与定时任务联动）
     *
     * <p>当 classpath 中存在 {@code CronjobServiceClient}（由 ydsz-pmis-cronjob-api 提供）且
     * {@code pmis.literule.action.cronjob-trigger-enabled=true}（默认 true）时自动装配。
     * 规则触发后自动触发关联的 cronjob 定时任务。
     *
     * @param cronjobClient cronjob Feign 客户端
     * @return CronjobTriggerActionHandler 实例
     * @since 2.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "cronjobServiceClient")
    @ConditionalOnProperty(prefix = "pmis.literule.action", name = "cronjob-trigger-enabled",
            havingValue = "true", matchIfMissing = true)
    public CronjobTriggerActionHandler cronjobTriggerActionHandler(
            CronjobServiceClient cronjobClient) {
        log.info("[LiteRule-Action] 定时任务触发处理器已初始化");
        return new CronjobTriggerActionHandler(cronjobClient);
    }

    /**
     * 工作流触发动作处理器 Bean（P2-1 规则与工作流深度联动）
     *
     * <p>当 classpath 中存在 {@code WorkflowServiceClient}（由 ydsz-pmis-workflow-api 提供）且
     * {@code pmis.literule.action.workflow-trigger-enabled=true}（默认 true）时自动装配。
     * 规则触发后自动启动关联的工作流流程实例。
     *
     * @param workflowClient workflow Feign 客户端
     * @return WorkflowTriggerActionHandler 实例
     * @since 2.1.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(name = "workflowServiceClient")
    @ConditionalOnProperty(prefix = "pmis.literule.action", name = "workflow-trigger-enabled",
            havingValue = "true", matchIfMissing = true)
    public WorkflowTriggerActionHandler workflowTriggerActionHandler(
            WorkflowServiceClient workflowClient) {
        log.info("[LiteRule-Action] 工作流触发处理器已初始化");
        return new WorkflowTriggerActionHandler(workflowClient);
    }

    // ------------------------------------------------------------------
    // P2-2 规则效果评估体系
    // ------------------------------------------------------------------

    /**
     * 规则效果评估服务（P2-2）
     *
     * <p>提供基于人工反馈标注的规则 Precision/Recall/F1 指标计算，
     * 支持滑动时间窗口、全局/单规则维度报告生成。
     *
     * <p>默认 7 天滑动窗口，可通过 {@code pmis.literule.effectiveness.window-days} 配置。
     *
     * @return RuleEffectivenessService 实例
     * @since 2.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "pmis.literule.effectiveness", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RuleEffectivenessService ruleEffectivenessService() {
        RuleEffectivenessService service = new RuleEffectivenessService();
        log.info("[LiteRule-Effectiveness] 规则效果评估服务已初始化（window=7天）");
        return service;
    }

    // ------------------------------------------------------------------
    // P2-3 高性能优化（评估结果缓存 + 规则分组并行评估）
    // ------------------------------------------------------------------

    /**
     * 评估结果缓存（P2-3）
     *
     * <p>当 {@code pmis.literule.performance.cache-enabled=true} 时装配，
     * 缓存规则引擎评估结果，相同上下文在 TTL 内复用缓存结果。
     *
     * @param properties 配置属性
     * @return EvaluationResultCache 实例
     * @since 2.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.performance", name = "cache-enabled", havingValue = "true")
    public EvaluationResultCache evaluationResultCache(LiteRuleProperties properties) {
        LiteRuleProperties.PerformanceConfig cfg = properties.getPerformance();
        EvaluationResultCache cache = new EvaluationResultCache(
                cfg.getCacheTtlSeconds() * 1000L, cfg.getCacheMaxSize());
        log.info("[LiteRule-Performance] 评估结果缓存已初始化（ttl={}s, maxSize={})",
                cfg.getCacheTtlSeconds(), cfg.getCacheMaxSize());
        return cache;
    }

    /**
     * 规则分组并行评估器（P2-3）
     *
     * <p>当 {@code pmis.literule.performance.parallel-enabled=true} 时装配，
     * 将候选规则按互斥组分组并行评估。
     *
     * @param properties 配置属性
     * @return ParallelRuleEvaluator 实例
     * @since 2.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.performance", name = "parallel-enabled", havingValue = "true")
    public ParallelRuleEvaluator parallelRuleEvaluator(LiteRuleProperties properties) {
        LiteRuleProperties.PerformanceConfig cfg = properties.getPerformance();
        ParallelRuleEvaluator evaluator = new ParallelRuleEvaluator(cfg.getParallelPoolSize());
        log.info("[LiteRule-Performance] 规则并行评估器已初始化（poolSize={})",
                cfg.getParallelPoolSize());
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
     * <p>退役检测基于规则执行统计（{@link RuleEngine#getStats()}），
     * 自动识别休眠规则、高错误率规则、长期停用规则和低影响规则，
     * 生成 {@link com.njydsz.pmis.literule.api.RetirementSuggestion} 建议列表。
     *
     * <p>可通过 {@code pmis.literule.lifecycle.enabled=false} 关闭。
     *
     * @param ruleEngine       规则引擎
     * @param configProvider   规则配置提供者
     * @param ruleAdminService 规则管理服务
     * @param versionRepoProvider 版本仓库（可选，未配置时不支持回滚预览）
     * @param properties       配置属性
     * @return RuleLifecycleService 实例
     * @since 2.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuleConfigProvider.class)
    @ConditionalOnProperty(
            prefix = "pmis.literule.lifecycle", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public RuleLifecycleService ruleLifecycleService(
            RuleEngine ruleEngine,
            RuleConfigProvider configProvider,
            RuleAdminService ruleAdminService,
            ObjectProvider<RuleVersionRepository> versionRepoProvider,
            LiteRuleProperties properties) {
        RuleLifecycleService service =
                new RuleLifecycleService(
                        ruleEngine, configProvider, ruleAdminService,
                        versionRepoProvider.getIfAvailable());
        service.configure(properties.getLifecycle());
        log.info("[LiteRule-Lifecycle] 规则生命周期管理服务已初始化（dormantMin={}, errorRateThreshold={}, staleDays={}, lowImpactRate={}）",
                properties.getLifecycle().getDormantMinEvaluations(),
                properties.getLifecycle().getHighErrorRateThreshold(),
                properties.getLifecycle().getStaleDisabledDays(),
                properties.getLifecycle().getLowImpactTriggerRate());
        return service;
    }

    // ------------------------------------------------------------------
    // P3-2 规则文档自动生成
    // ------------------------------------------------------------------

    /**
     * 规则文档自动生成服务（P3-2）
     *
     * <p>当存在 {@link RuleConfigProvider} 时自动装配，
     * 从规则元数据、版本历史、执行统计自动生成结构化文档，
     * 支持 Markdown / HTML 输出格式。
     *
     * <p>可通过 {@code pmis.literule.lifecycle.enabled=false} 关闭（复用生命周期开关）。
     *
     * @param configProvider   规则配置提供者
     * @param ruleEngine       规则引擎
     * @param versionRepoProvider 版本仓库（可选）
     * @param effectivenessServiceProvider 效果评估服务（可选）
     * @return RuleDocumentationService 实例
     * @since 2.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuleConfigProvider.class)
    public RuleDocumentationService ruleDocumentationService(
            RuleConfigProvider configProvider,
            RuleEngine ruleEngine,
            ObjectProvider<RuleVersionRepository> versionRepoProvider,
            ObjectProvider<RuleEffectivenessService> effectivenessServiceProvider) {
        RuleDocumentationService service =
                new RuleDocumentationService(
                        configProvider, ruleEngine, versionRepoProvider.getIfAvailable());
        RuleEffectivenessService effectivenessService =
                effectivenessServiceProvider.getIfAvailable();
        if (effectivenessService != null) {
            service.setEffectivenessService(effectivenessService);
        }
        log.info("[LiteRule-DocGen] 规则文档自动生成服务已初始化（versionRepo={}, effectiveness={}）",
                versionRepoProvider.getIfAvailable() != null, effectivenessService != null);
        return service;
    }

    // ------------------------------------------------------------------
    // P3-4 执行回放服务
    // ------------------------------------------------------------------

    /**
     * 执行回放服务（P3-4）
     *
     * <p>当存在 {@link RuleAdminService} 时自动装配，
     * 提供基于历史执行轨迹的事实快照重新评估规则的能力。
     *
     * @param ruleAdminService   规则管理服务
     * @param traceRecorderProvider 轨迹记录器（可选）
     * @param versionRepoProvider   版本仓库（可选，支持版本回放）
     * @param evaluator         表达式求值器
     * @return ExecutionReplayService 实例
     * @since 2.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuleAdminService.class)
    public ExecutionReplayService executionReplayService(
            RuleAdminService ruleAdminService,
            ObjectProvider<TraceRecorder> traceRecorderProvider,
            ObjectProvider<RuleVersionRepository> versionRepoProvider,
            ExpressionEvaluator evaluator) {
        ExecutionReplayService service =
                new ExecutionReplayService(
                        ruleAdminService,
                        traceRecorderProvider.getIfAvailable(),
                        versionRepoProvider.getIfAvailable(),
                        evaluator);
        log.info("[LiteRule-Replay] 执行回放服务已初始化（traceRecorder={}, versionRepo={}）",
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
     * <p>当存在 {@link RuleAdminService} 时自动装配，
     * 记录规则全生命周期操作的审计日志。
     * 默认使用内存存储，可通过 {@link com.njydsz.pmis.literule.server.audit.RuleAuditLogService.AuditLogStore}
     * SPI 提供持久化实现。
     *
     * @param auditLogStoreProvider 审计日志存储（可选，为空使用内存存储）
     * @return RuleAuditLogService 实例
     * @since 2.0.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuleAdminService.class)
    public RuleAuditLogService ruleAuditLogService(
            ObjectProvider<RuleAuditLogService.AuditLogStore> auditLogStoreProvider) {
        RuleAuditLogService.AuditLogStore store =
                auditLogStoreProvider.getIfAvailable();
        RuleAuditLogService service =
                new RuleAuditLogService(store);
        log.info("[LiteRule-Audit] 规则审计日志服务已初始化（store={}）",
                store != null ? store.getClass().getSimpleName() : "InMemory");
        return service;
    }
}
