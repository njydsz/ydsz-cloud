package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.adaptive.AdaptiveThresholdService;
import com.njydsz.pmis.literule.agent.AgentRuleNode;
import com.njydsz.pmis.literule.agent.AgentRuleNodeFactory;
import com.njydsz.pmis.literule.agent.ReActAgentExecutor;
import com.njydsz.pmis.literule.ai.LLMClient;
import com.njydsz.pmis.literule.ai.MockLLMClient;
import com.njydsz.pmis.literule.ai.OpenAICompatibleLLMClient;
import com.njydsz.pmis.literule.ai.RuleAttributionService;
import com.njydsz.pmis.literule.ai.RuleHealthScoreService;
import com.njydsz.pmis.literule.ai.RuleLLMService;
import com.njydsz.pmis.literule.ai.RuleRecommendationService;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.approval.ApprovalPermissionChecker;
import com.njydsz.pmis.literule.approval.ApprovalRecordRepository;
import com.njydsz.pmis.literule.approval.RuleApprovalService;
import com.njydsz.pmis.literule.cache.CachingRuleConfigProvider;
import com.njydsz.pmis.literule.cep.CEPEngine;
import com.njydsz.pmis.literule.core.AsyncTraceRecorder;
import com.njydsz.pmis.literule.core.BreakpointHook;
import com.njydsz.pmis.literule.core.DefaultBreakpointHook;
import com.njydsz.pmis.literule.core.DefaultRuleEngine;
import com.njydsz.pmis.literule.core.MicrometerRuleMetrics;
import com.njydsz.pmis.literule.core.RuleCanaryRouter;
import com.njydsz.pmis.literule.core.RuleCircuitBreaker;
import com.njydsz.pmis.literule.core.RuleMetrics;
import com.njydsz.pmis.literule.core.RuleTimeoutExecutor;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.EmptyVariableRegistry;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionValidationService;
import com.njydsz.pmis.literule.expr.QLExpressExpressionEvaluator;
import com.njydsz.pmis.literule.expr.VariableRegistry;
import com.njydsz.pmis.literule.model.ModelInputProvider;
import com.njydsz.pmis.literule.model.ModelInputRegistry;
import com.njydsz.pmis.literule.model.MockModelInputProvider;
import com.njydsz.pmis.literule.spi.DecisionTableConfigProvider;
import com.njydsz.pmis.literule.spi.DecisionTreeConfigProvider;
import com.njydsz.pmis.literule.spi.FileRuleSource;
import com.njydsz.pmis.literule.spi.RuleConfigBroadcaster;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import com.njydsz.pmis.literule.spi.ScorecardConfigProvider;
import com.njydsz.pmis.literule.spi.ScriptConfigProvider;
import com.njydsz.pmis.literule.spi.TraceDataProvider;
import com.njydsz.pmis.literule.spi.TraceRecorder;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;
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
     * 表达式求值器
     *
     * <p>根据 {@code pmis.literule.evaluator} 配置选择实现：
     * <ul>
     *   <li>{@code aviator}（默认）- {@link AviatorExpressionEvaluator}，高性能、AST 缓存</li>
     *   <li>{@code qlexpress} - {@link QLExpressExpressionEvaluator}，
     *       阿里 QLExpress，语法接近 Java，支持流程控制</li>
     * </ul>
     *
     * @param properties 配置属性
     * @return ExpressionEvaluator 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ExpressionEvaluator expressionEvaluator(LiteRuleProperties properties) {
        String type = properties.getEvaluator() == null ? "aviator" : properties.getEvaluator().trim().toLowerCase();
        switch (type) {
            case "qlexpress" -> {
                log.info("[LiteRule] QLExpress 表达式求值器已初始化（sandbox={}）", properties.isSandboxEnabled());
                return new QLExpressExpressionEvaluator();
            }
            case "aviator" -> {
                log.info("[LiteRule] Aviator 表达式求值器已初始化（sandbox={}）", properties.isSandboxEnabled());
                return new AviatorExpressionEvaluator(properties.isSandboxEnabled());
            }
            default -> {
                log.warn("[LiteRule] 未知表达式引擎类型: {}，回退到 Aviator", properties.getEvaluator());
                return new AviatorExpressionEvaluator(properties.isSandboxEnabled());
            }
        }
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
                                  ObjectProvider<BreakpointHook> breakpointHookProvider,
                                  ObjectProvider<ModelInputRegistry> modelRegistryProvider,
                                  ApplicationContext applicationContext) {
        DefaultRuleEngine engine = new DefaultRuleEngine();
        engine.setStatsEnabled(properties.isStatsEnabled());

        // 断点调试 Hook（P2-3）：可选注入，仅当应用层提供实现时生效
        BreakpointHook bpHook = breakpointHookProvider.getIfAvailable();
        if (bpHook != null) {
            engine.setBreakpointHook(bpHook);
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
                evaluator = expressionEvaluator(properties);
            }
            RuleCanaryRouter canaryRouter = new RuleCanaryRouter(evaluator);
            engine.setCanaryRouter(canaryRouter);
            engine.setCanaryEnabled(true);
            log.info("[LiteRule] 规则灰度路由已启用");
        }

        // Micrometer 桥接（仅当 classpath 存在 MeterRegistry 时启用）
        bindMicrometerIfAvailable(engine, applicationContext);

        log.info("[LiteRule] 默认规则引擎已初始化（statsEnabled={}, traceEnabled={}, timeoutMs={}, breaker={}, metrics={}, canary={}, breakpoint={}, model={}）",
                properties.isStatsEnabled(), properties.isTraceEnabled(),
                properties.getRuleTimeoutMs(), properties.getCircuitBreakerMinEvaluations() > 0,
                engine.getMetrics() != null, engine.getCanaryRouter() != null,
                engine.getBreakpointHook() != null, engine.getModelInputRegistry() != null);
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
            ObjectProvider<ApprovalPermissionChecker> permissionCheckerProvider) {
        RuleApprovalService service = new RuleApprovalService(configProvider);
        ApprovalRecordRepository recordRepo = recordRepoProvider.getIfAvailable();
        if (recordRepo != null) {
            service.setRecordRepository(recordRepo);
        }
        ApprovalPermissionChecker checker = permissionCheckerProvider.getIfAvailable();
        if (checker != null) {
            service.setPermissionChecker(checker);
        }
        log.info("[LiteRule-Approval] 规则审批流服务已初始化（recordRepository={}, permissionChecker={}）",
                recordRepo != null, checker != null);
        return service;
    }

    // ------------------------------------------------------------------
    // P2-15 AI 增强
    // ------------------------------------------------------------------

    /**
     * LLM 客户端（P2-15）
     *
     * <p>根据 {@code pmis.literule.ai.llm-client} 配置选择实现：
     * <ul>
     *   <li>OPENAI_COMPATIBLE：{@link OpenAICompatibleLLMClient}（OpenAI/DeepSeek/通义千问/Ollama 等兼容协议）</li>
     *   <li>MOCK（默认）：{@link MockLLMClient}（离线/单元测试）</li>
     * </ul>
     *
     * @param properties 配置
     * @return LLMClient 实例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    public LLMClient llmClient(LiteRuleProperties properties) {
        LiteRuleProperties.Ai ai = properties.getAi();
        String type = ai.getLlmClient();
        if (type == null || type.isEmpty() || "MOCK".equalsIgnoreCase(type)) {
            log.info("[LiteRule-AI] LLM 客户端使用 Mock 实现（provider=MOCK, model={}）",
                    MockLLMClient.DEFAULT_MODEL);
            return new MockLLMClient();
        }
        if ("OPENAI_COMPATIBLE".equalsIgnoreCase(type)) {
            log.info("[LiteRule-AI] LLM 客户端使用 OpenAI 兼容协议（apiUrl={}, model={}）",
                    ai.getLlmApiUrl(), ai.getLlmModel());
            return new OpenAICompatibleLLMClient(ai);
        }
        log.warn("[LiteRule-AI] 未知的 llm-client 类型: {}，回退到 MOCK", type);
        return new MockLLMClient();
    }

    /**
     * 规则 LLM 服务（P2-15）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    public RuleLLMService ruleLLMService(LLMClient llmClient,
                                          ExpressionValidationService expressionValidationService) {
        log.info("[LiteRule-AI] 规则 LLM 服务已初始化（provider={}, model={}）",
                llmClient.provider(), llmClient.model());
        return new RuleLLMService(llmClient, expressionValidationService);
    }

    /**
     * 规则健康度评分服务（P2-15）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    public RuleHealthScoreService ruleHealthScoreService(LiteRuleProperties properties) {
        log.info("[LiteRule-AI] 规则健康度评分服务已初始化（hitRateWeight={}, errorRateWeight={}, complexityWeight={}, coverageWeight={}）",
                properties.getAi().getHealthHitRateWeight(),
                properties.getAi().getHealthErrorRateWeight(),
                properties.getAi().getHealthComplexityWeight(),
                properties.getAi().getHealthCoverageWeight());
        return new RuleHealthScoreService(properties.getAi());
    }

    /**
     * 规则推荐服务（P2-15）
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    public RuleRecommendationService ruleRecommendationService(LiteRuleProperties properties) {
        log.info("[LiteRule-AI] 规则推荐服务已初始化（topN={}）",
                properties.getAi().getRecommendTopN());
        return new RuleRecommendationService(properties.getAi());
    }

    /**
     * ReAct Agent 执行器（P3-5 AI Agent 规则编排）
     *
     * <p>依赖 {@link LLMClient}，仅当 AI 增强启用且 LLMClient Bean 存在时装配。
     * 提供 ReAct 推理循环能力，供 {@link AgentRuleNode} 调用。
     *
     * @param llmClient LLM 客户端
     * @return ReActAgentExecutor 实例
     * @since 1.8.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LLMClient.class)
    public ReActAgentExecutor reActAgentExecutor(LLMClient llmClient) {
        log.info("[LiteRule-Agent] ReAct Agent 执行器已初始化（provider={}, model={}）",
                llmClient.provider(), llmClient.model());
        return new ReActAgentExecutor(llmClient);
    }

    /**
     * AgentRuleNode 工厂（P3-5）
     *
     * <p>依赖 {@link ReActAgentExecutor}，
     * 提供快速创建 {@link AgentRuleNode} 的便捷方法。
     *
     * @param executor ReAct 执行器
     * @return AgentRuleNodeFactory 实例
     * @since 1.8.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ReActAgentExecutor.class)
    public AgentRuleNodeFactory agentRuleNodeFactory(
            ReActAgentExecutor executor) {
        log.info("[LiteRule-Agent] AgentRuleNode 工厂已初始化");
        return new AgentRuleNodeFactory(executor);
    }

    /**
     * 规则归因分析服务（P3-3 LLM 辅助归因分析）
     *
     * <p>当存在 {@link RuleAdminService} 时自动装配。基础归因（summary + factors）
     * 不依赖 LLM；LLM 可用时附加 llmAnalysis 和 recommendation。
     *
     * @param ruleAdminService   规则管理服务
     * @param llmClientProvider  LLM 客户端（可选，未启用 AI 时为空）
     * @return RuleAttributionService 实例
     * @since 1.8.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RuleAdminService.class)
    public RuleAttributionService ruleAttributionService(
            RuleAdminService ruleAdminService,
            ObjectProvider<LLMClient> llmClientProvider) {
        LLMClient llmClient = llmClientProvider.getIfAvailable();
        log.info("[LiteRule-AI] 规则归因分析服务已初始化（llmEnabled={}）", llmClient != null);
        return new RuleAttributionService(ruleAdminService, llmClient);
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
            java.util.List<RuleConfigProvider> providers,
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
     * 通过 {@code pmis.literule.annotation-scan-base-packages} 指定扫描基包（逗号分隔）。
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
        log.info("[LiteRule-Annotation] 声明式规则注册器已初始化（scanBasePackages={}）",
                properties.getAnnotationScanBasePackages());
        return registrar;
    }

    // ------------------------------------------------------------------
    // P3-4 自适应智能风控（自适应阈值分析）
    // ------------------------------------------------------------------

    /**
     * 自适应阈值分析服务（P3-4）
     *
     * <p>当存在 {@link RuleConfigProvider} 和
     * {@link TraceDataProvider} 时自动装配，
     * 提供基于历史触发数据的规则阈值自适应调整能力。
     *
     * <p>对标字节巨量引擎"规则 2.0"的自适应阈值能力：
     * <ul>
     *   <li>分析规则历史触发数据，计算最优阈值</li>
     *   <li>支持 PERCENTILE/FALSE_RATE/MISS_RATE/BALANCED 四种策略</li>
     *   <li>LLM 可用时生成自然语言调整原因，不可用时降级为模板</li>
     *   <li>支持一键应用阈值调整</li>
     * </ul>
     *
     * @param configProvider        规则配置提供者
     * @param traceDataProvider     轨迹数据提供者（SPI，由消费方提供）
     * @param ruleAdminServiceProvider 规则管理服务（可选，仅 applyThreshold 需要）
     * @param llmClientProvider     LLM 客户端（可选，用于生成调整原因）
     * @return AdaptiveThresholdService 实例
     * @since 1.8.0
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({RuleConfigProvider.class,
            TraceDataProvider.class})
    public AdaptiveThresholdService adaptiveThresholdService(
            RuleConfigProvider configProvider,
            TraceDataProvider traceDataProvider,
            ObjectProvider<RuleAdminService> ruleAdminServiceProvider,
            ObjectProvider<LLMClient> llmClientProvider) {
        RuleAdminService ruleAdminService = ruleAdminServiceProvider.getIfAvailable();
        LLMClient llmClient = llmClientProvider.getIfAvailable();
        AdaptiveThresholdService service =
                new AdaptiveThresholdService(
                        configProvider, traceDataProvider, ruleAdminService, llmClient);
        log.info("[LiteRule-Adaptive] 自适应阈值分析服务已初始化（ruleAdmin={}, llm={}）",
                ruleAdminService != null, llmClient != null);
        return service;
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
}
