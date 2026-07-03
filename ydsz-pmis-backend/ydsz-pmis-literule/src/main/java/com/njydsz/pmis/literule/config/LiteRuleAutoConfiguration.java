package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.core.AsyncTraceRecorder;
import com.njydsz.pmis.literule.core.DefaultRuleEngine;
import com.njydsz.pmis.literule.core.MicrometerRuleMetrics;
import com.njydsz.pmis.literule.core.RuleCanaryRouter;
import com.njydsz.pmis.literule.core.RuleCircuitBreaker;
import com.njydsz.pmis.literule.core.RuleMetrics;
import com.njydsz.pmis.literule.core.RuleTimeoutExecutor;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.spi.DecisionTreeConfigProvider;
import com.njydsz.pmis.literule.spi.RuleConfigBroadcaster;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import com.njydsz.pmis.literule.spi.ScorecardConfigProvider;
import com.njydsz.pmis.literule.spi.ScriptConfigProvider;
import com.njydsz.pmis.literule.spi.TraceRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * LiteRule 自动配置
 *
 * <p>自动注册核心组件：表达式求值器、规则引擎、规则管理服务。
 * 当 classpath 中存在 RuleConfigProvider 实现时，自动启用动态规则加载和热刷新。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(LiteRuleProperties.class)
@ConditionalOnProperty(prefix = "pmis.literule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiteRuleAutoConfiguration {

    /**
     * 表达式求值器（Aviator）
     *
     * @return AviatorExpressionEvaluator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionEvaluator expressionEvaluator(LiteRuleProperties properties) {
        log.info("[LiteRule] Aviator 表达式求值器已初始化（sandbox={}）", properties.isSandboxEnabled());
        return new AviatorExpressionEvaluator(properties.isSandboxEnabled());
    }

    /**
     * 规则引擎
     *
     * <p>1.4.0 起：
     * <ul>
     *   <li>当 traceEnabled=true 时自动装配 {@link AsyncTraceRecorder}，
     *       若消费方提供了自定义 {@link TraceRecorder} Bean 则作为持久化委托</li>
     *   <li>当 ruleTimeoutMs > 0 时启用 {@link RuleTimeoutExecutor}</li>
     *   <li>当 circuitBreakerMinEvaluations > 0 时启用 {@link RuleCircuitBreaker}</li>
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
                                  ObjectProvider<com.njydsz.pmis.literule.core.BreakpointHook> breakpointHookProvider,
                                  ApplicationContext applicationContext) {
        DefaultRuleEngine engine = new DefaultRuleEngine();
        engine.setStatsEnabled(properties.isStatsEnabled());

        // 断点调试 Hook（P2-3）：可选注入，仅当应用层提供实现时生效
        com.njydsz.pmis.literule.core.BreakpointHook bpHook = breakpointHookProvider.getIfAvailable();
        if (bpHook != null) {
            engine.setBreakpointHook(bpHook);
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
            RuleCircuitBreaker breaker = new RuleCircuitBreaker(
                    properties.getCircuitBreakerErrorRate(),
                    properties.getCircuitBreakerMinEvaluations(),
                    30_000L);
            engine.setCircuitBreaker(breaker);
            log.info("[LiteRule] 规则熔断器已启用 (errorRateThreshold={}, minEvaluations={})",
                    properties.getCircuitBreakerErrorRate(), properties.getCircuitBreakerMinEvaluations());
        }

        if (properties.isCanaryEnabled()) {
            ExpressionEvaluator evaluator = evaluatorProvider.getIfAvailable();
            if (evaluator == null) {
                evaluator = new AviatorExpressionEvaluator(properties.isSandboxEnabled());
            }
            RuleCanaryRouter canaryRouter = new RuleCanaryRouter(evaluator);
            engine.setCanaryRouter(canaryRouter);
            engine.setCanaryEnabled(true);
            log.info("[LiteRule] 规则灰度路由已启用");
        }

        // Micrometer 桥接（仅当 classpath 存在 MeterRegistry 时启用）
        bindMicrometerIfAvailable(engine, applicationContext);

        log.info("[LiteRule] 默认规则引擎已初始化（statsEnabled={}, traceEnabled={}, timeoutMs={}, breaker={}, metrics={}, canary={}, breakpoint={}）",
                properties.isStatsEnabled(), properties.isTraceEnabled(),
                properties.getRuleTimeoutMs(), properties.getCircuitBreakerMinEvaluations() > 0,
                engine.getMetrics() != null, engine.getCanaryRouter() != null,
                engine.getBreakpointHook() != null);
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
                    "com.njydsz.pmis.literule.core.MicrometerRuleMetrics", true,
                    getClass().getClassLoader());
            java.lang.reflect.Constructor<?> ctor = metricsClass.getConstructor(meterRegistryClass);
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
     * @since 1.3.0
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
     * 当 classpath 中存在 {@link com.njydsz.pmis.literule.expr.VariableRegistry} Bean 时，
     * 启用 UNDEFINED_VARIABLE 校验；否则使用 {@link com.njydsz.pmis.literule.expr.EmptyVariableRegistry} 跳过。
     *
     * @param evaluator 表达式求值器
     * @param registryProvider 变量注册表（可选）
     * @return ExpressionValidationService 实例
     * @since 1.4.0
     */
    @Bean
    @ConditionalOnMissingBean
    public com.njydsz.pmis.literule.expr.ExpressionValidationService expressionValidationService(
            ExpressionEvaluator evaluator,
            org.springframework.beans.factory.ObjectProvider<com.njydsz.pmis.literule.expr.VariableRegistry> registryProvider) {
        com.njydsz.pmis.literule.expr.VariableRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            registry = new com.njydsz.pmis.literule.expr.EmptyVariableRegistry();
            log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验未启用）");
        } else {
            log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验已启用，已注册 {} 个变量）",
                    registry.listAll().size());
        }
        return new com.njydsz.pmis.literule.expr.ExpressionValidationService(evaluator, registry);
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
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RuleConfigProvider.class)
    public RuleHotReloader ruleHotReloader(RuleEngine ruleEngine,
                                            ExpressionEvaluator evaluator,
                                            RuleConfigProvider configProvider,
                                            org.springframework.beans.factory.ObjectProvider<DecisionTableConfigProvider> dtConfigProvider,
                                            org.springframework.beans.factory.ObjectProvider<ScorecardConfigProvider> scConfigProvider,
                                            org.springframework.beans.factory.ObjectProvider<DecisionTreeConfigProvider> trConfigProvider,
                                            org.springframework.beans.factory.ObjectProvider<ScriptConfigProvider> scriptConfigProvider,
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
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(DecisionTableConfigProvider.class)
    public DecisionTableAdminService decisionTableAdminService(RuleEngine ruleEngine,
                                                                DecisionTableConfigProvider dtConfigProvider,
                                                                org.springframework.beans.factory.ObjectProvider<RuleConfigBroadcaster> broadcasterProvider,
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
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(RuleConfigProvider.class)
    public RuleAdminService ruleAdminService(RuleEngine ruleEngine,
                                              ExpressionEvaluator evaluator,
                                              RuleConfigProvider configProvider,
                                              org.springframework.beans.factory.ObjectProvider<RuleVersionRepository> versionRepoProvider,
                                              org.springframework.beans.factory.ObjectProvider<RuleConfigBroadcaster> broadcasterProvider,
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
}
