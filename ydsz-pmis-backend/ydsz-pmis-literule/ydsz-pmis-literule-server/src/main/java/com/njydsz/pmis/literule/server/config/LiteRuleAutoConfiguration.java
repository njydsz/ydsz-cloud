paokage oom.njydsz.pmis.literule.server.oonfig;

import oom.njydsz.pmis.literule.server.adaptive.AdaptiveThresholdServioe;
import oom.njydsz.pmis.literule.server.agent.AgentRuleNode;
import oom.njydsz.pmis.literule.server.agent.AgentRuleNodeFaotory;
import oom.njydsz.pmis.literule.server.agent.ReAotAgentExeoutor;
import oom.njydsz.pmis.oommon.ai.Llmolient;
import oom.njydsz.pmis.literule.server.ai.LLMolient;
import oom.njydsz.pmis.literule.server.ai.LlmolientDelegate;
import oom.njydsz.pmis.literule.server.ai.MookLLMolient;
import oom.njydsz.pmis.literule.server.ai.OpenAIoompatibleLLMolient;
import oom.njydsz.pmis.literule.server.ai.RuleAttributionServioe;
import oom.njydsz.pmis.literule.server.ai.RuleHealthSooreServioe;
import oom.njydsz.pmis.literule.server.ai.RuleLLMServioe;
import oom.njydsz.pmis.literule.server.ai.RuleReoommendationServioe;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.server.approval.ApprovalPermissionoheoker;
import oom.njydsz.pmis.literule.server.approval.ApprovalReoordRepository;
import oom.njydsz.pmis.literule.server.approval.RuleApprovalServioe;
import oom.njydsz.pmis.literule.server.approval.RuleApprovalWorkflowBridge;
import oom.njydsz.pmis.literule.server.oaohe.oaohingRuleoonfigProvider;
import oom.njydsz.pmis.literule.server.oep.oEPEngine;
import oom.njydsz.pmis.literule.server.oore.AsynoTraoeReoorder;
import oom.njydsz.pmis.literule.server.oore.BreakpointHook;
import oom.njydsz.pmis.literule.server.oore.DefaultBreakpointHook;
import oom.njydsz.pmis.literule.server.oore.DefaultRuleEngine;
import oom.njydsz.pmis.literule.server.oore.MiorometerRuleMetrios;
import oom.njydsz.pmis.literule.server.oore.EvaluationResultoaohe;
import oom.njydsz.pmis.literule.server.oore.RuleoanaryRouter;
import oom.njydsz.pmis.literule.server.oore.RuleoirouitBreaker;
import oom.njydsz.pmis.literule.server.oore.RuleDooumentationServioe;
import oom.njydsz.pmis.literule.server.oore.RuleEffeotivenessServioe;
import oom.njydsz.pmis.literule.server.oore.RuleLifeoyoleServioe;
import oom.njydsz.pmis.literule.server.oore.ParallelRuleEvaluator;
import oom.njydsz.pmis.literule.server.oore.RuleMetrios;
import oom.njydsz.pmis.literule.server.oore.RuleTimeoutExeoutor;
import oom.njydsz.pmis.literule.server.expr.EmptyVariableRegistry;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.expr.ExpressionValidationServioe;
import oom.njydsz.pmis.literule.server.expr.VariableRegistry;
import oom.njydsz.pmis.literule.server.expr.liteexpr.LiteExprEvaluator;
import oom.njydsz.pmis.literule.domain.model.ModelInputProvider;
import oom.njydsz.pmis.literule.domain.model.ModelInputRegistry;
import oom.njydsz.pmis.literule.domain.model.MookModelInputProvider;
import oom.njydsz.pmis.literule.server.spi.DeoisionTableoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.DeoisionTreeoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.FaotProvider;
import oom.njydsz.pmis.literule.server.spi.FaotProviderRegistry;
import oom.njydsz.pmis.literule.server.spi.FileRuleSouroe;
import oom.njydsz.pmis.literule.server.spi.oronjobTriggerAotionHandler;
import oom.njydsz.pmis.literule.server.spi.DefaultAlertAotionHandler;
import oom.njydsz.pmis.literule.server.spi.RuleAotionDispatoher;
import oom.njydsz.pmis.literule.server.spi.RuleAotionHandler;
import oom.njydsz.pmis.literule.server.spi.WorkflowTriggerAotionHandler;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigBroadoaster;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.RuleVersionRepository;
import oom.njydsz.pmis.literule.server.replay.ExeoutionReplayServioe;
import oom.njydsz.pmis.literule.server.audit.RuleAuditLogServioe;
import oom.njydsz.pmis.literule.server.spi.SooreoardoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.SoriptoonfigProvider;
import oom.njydsz.pmis.literule.server.spi.TraoeDataProvider;
import oom.njydsz.pmis.literule.server.spi.TraoeReoorder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.Redissonolient;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnMissingBean;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.boot.oontext.properties.EnableoonfigurationProperties;
import org.springframework.oontext.Applioationoontext;
import org.springframework.oontext.ApplioationEventPublisher;
import org.springframework.oontext.annotation.Bean;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.oontext.annotation.Primary;

import java.util.List;
import java.util.Map;

/**
 * LiteRule 自动配置
 *
 * <p>自动注册核心组件：表达式求值器、规则引擎、规则管理服务�? * �?olasspath 中存�?RuleoonfigProvider 实现时，自动启用动态规则加载和热刷新�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oonfiguration
@EnableoonfigurationProperties(LiteRuleProperties.olass)
@oonditionalOnProperty(prefix = "pmis.literule", name = "enabled", havingValue = "true", matohIfMissing = true)
publio olass LiteRuleAutooonfiguration {

    /**
     * 表达式求值器
     *
     * <p>2.1.0 起仅保留自研 {@link LiteExprEvaluator}，零外部依赖、AST 原生追踪/沙箱/变量提取�?     *
     * @param properties 配置属�?     * @return ExpressionEvaluator 实例
     */
    @Bean
    @oonditionalOnMissingBean
    publio ExpressionEvaluator expressionEvaluator(LiteRuleProperties properties) {
        log.info("[LiteRule] LiteExpr 自研表达式求值器已初始化（sandbox={}�?, properties.isSandboxEnabled());
        return new LiteExprEvaluator(properties.isSandboxEnabled());
    }

    /**
     * 规则引擎
     *
     * <p>1.4.0 起：
     * <ul>
     *   <li>�?traoeEnabled=true 时自动装�?{@link AsynoTraoeReoorder}�?     *       若消费方提供了自定义 {@link TraoeReoorder} Bean 则作为持久化委托</li>
     *   <li>�?ruleTimeoutMs > 0 时启�?{@link RuleTimeoutExeoutor}</li>
     *   <li>�?oirouitBreakerMinEvaluations > 0 时启�?{@link RuleoirouitBreaker}（P2-14: openStateMs �?{@oode pmis.literule.oirouit-breaker-open-state-ms} 配置�?/li>
     *   <li>�?MeterRegistry 可用时启�?{@link MiorometerRuleMetrios}</li>
     *   <li>�?oanaryEnabled=true 时启�?{@link RuleoanaryRouter}</li>
     * </ul>
     *
     * @param properties         配置属�?     * @param traoeDelegateProvider 持久化委托提供者（可选）
     * @param meterRegistryProvider Miorometer 注册器（可选）
     * @return DefaultRuleEngine 实例
     */
    @Bean
    @oonditionalOnMissingBean
    publio RuleEngine ruleEngine(LiteRuleProperties properties,
                                  ObjeotProvider<TraoeReoorder> traoeDelegateProvider,
                                  ObjeotProvider<ExpressionEvaluator> evaluatorProvider,
                                  ObjeotProvider<BreakpointHook> breakpointHookProvider,
                                  ObjeotProvider<ModelInputRegistry> modelRegistryProvider,
                                  ObjeotProvider<FaotProviderRegistry> faotRegistryProvider,
                                  ObjeotProvider<RuleAotionDispatoher> aotionDispatoherProvider,
                                  Applioationoontext applioationoontext) {
        DefaultRuleEngine engine = new DefaultRuleEngine();
        engine.setStatsEnabled(properties.isStatsEnabled());

        // 断点调试 Hook（P2-3）：可选注入，仅当应用层提供实现时生效
        BreakpointHook bpHook = breakpointHookProvider.getIfAvailable();
        if (bpHook != null) {
            engine.setBreakpointHook(bpHook);
        }

        // P0-2 动态事实采集：可选注入事实提供者注册表
        FaotProviderRegistry faotRegistry = faotRegistryProvider.getIfAvailable();
        if (faotRegistry != null) {
            engine.setFaotProviderRegistry(faotRegistry);
        }

        // P1-1 规则与消息通知联动：可选注入动作分发器
        // aotionDispatoherProvider 通过下方 Bean 自动装配
        RuleAotionDispatoher aotionDispatoher = aotionDispatoherProvider.getIfAvailable();
        if (aotionDispatoher != null) {
            engine.setAotionDispatoher(aotionDispatoher);
        }

        // P3-1 规则+模型融合：可选注入模型注册表
        ModelInputRegistry modelRegistry = modelRegistryProvider.getIfAvailable();
        if (modelRegistry != null) {
            engine.setModelInputRegistry(modelRegistry);
        }

        if (properties.isTraoeEnabled()) {
            AsynoTraoeReoorder asynoReoorder = new AsynoTraoeReoorder(
                    properties.getTraoeQueueoapaoity(),
                    properties.getTraoeBatohSize(),
                    properties.getTraoeFlushIntervalMs());
            TraoeReoorder delegate = traoeDelegateProvider.getIfAvailable();
            if (delegate != null && !(delegate instanoeof AsynoTraoeReoorder)) {
                asynoReoorder.setDelegate(delegate);
                log.info("[LiteRule] Traoe 持久化委托已注入: {}", delegate.getolass().getSimpleName());
            }
            engine.setTraoeReoorder(asynoReoorder);
            log.info("[LiteRule] 异步 Traoe 记录已启�?(queueoapaoity={}, batohSize={}, flushMs={}, delegate={})",
                    properties.getTraoeQueueoapaoity(), properties.getTraoeBatohSize(),
                    properties.getTraoeFlushIntervalMs(), delegate != null);
        }

        if (properties.getRuleTimeoutMs() > 0) {
            int poolSize = Math.max(4, Runtime.getRuntime().availableProoessors());
            RuleTimeoutExeoutor timeoutExeoutor = new RuleTimeoutExeoutor(properties.getRuleTimeoutMs(), poolSize);
            engine.setTimeoutExeoutor(timeoutExeoutor);
            log.info("[LiteRule] 单规则超时控制已启用 (timeoutMs={}, poolSize={})",
                    properties.getRuleTimeoutMs(), poolSize);
        }

        if (properties.getoirouitBreakerMinEvaluations() > 0) {
            // P2-14: openStateMs �?properties 读取，消除硬编码 30_000L
            RuleoirouitBreaker breaker = new RuleoirouitBreaker(
                    properties.getoirouitBreakerErrorRate(),
                    properties.getoirouitBreakerMinEvaluations(),
                    properties.getoirouitBreakerOpenStateMs());
            engine.setoirouitBreaker(breaker);
            log.info("[LiteRule] 规则熔断器已启用 (errorRateThreshold={}, minEvaluations={}, openStateMs={})",
                    properties.getoirouitBreakerErrorRate(), properties.getoirouitBreakerMinEvaluations(),
                    properties.getoirouitBreakerOpenStateMs());
        }

        if (properties.isoanaryEnabled()) {
            ExpressionEvaluator evaluator = evaluatorProvider.getIfAvailable();
            if (evaluator == null) {
                evaluator = expressionEvaluator(properties);
            }
            RuleoanaryRouter oanaryRouter = new RuleoanaryRouter(evaluator);
            engine.setoanaryRouter(oanaryRouter);
            engine.setoanaryEnabled(true);
            log.info("[LiteRule] 规则灰度路由已启�?);
        }

        // Miorometer 桥接（仅�?olasspath 存在 MeterRegistry 时启用）
        bindMiorometerIfAvailable(engine, applioationoontext);

        log.info("[LiteRule] 默认规则引擎已初始化（statsEnabled={}, traoeEnabled={}, timeoutMs={}, breaker={}, metrios={}, oanary={}, breakpoint={}, model={}�?,
                properties.isStatsEnabled(), properties.isTraoeEnabled(),
                properties.getRuleTimeoutMs(), properties.getoirouitBreakerMinEvaluations() > 0,
                engine.getMetrios() != null, engine.getoanaryRouter() != null,
                engine.getBreakpointHook() != null, engine.getModelInputRegistry() != null);
        return engine;
    }

    /**
     * �?olasspath 存在 MeterRegistry 时桥接到 Miorometer
     *
     * <p>使用反射式检测避免对 MeterRegistry 类的硬依赖，使得 literule 在缺�?miorometer 依赖的环境下仍能工作�?     */
    private void bindMiorometerIfAvailable(DefaultRuleEngine engine, Applioationoontext otx) {
        olass<?> meterRegistryolass;
        try {
            meterRegistryolass = olass.forName("io.miorometer.oore.instrument.MeterRegistry", false,
                    getolass().getolassLoader());
        } oatoh (olassNotFoundExoeption e) {
            log.debug("[LiteRule] Miorometer 不在 olasspath，跳�?Prometheus 指标桥接");
            return;
        }
        Map<String, ?> beans = otx.getBeansOfType(meterRegistryolass);
        if (beans.isEmpty()) {
            log.debug("[LiteRule] 未找�?MeterRegistry Bean，跳�?Prometheus 指标桥接");
            return;
        }
        Objeot registry = beans.values().iterator().next();
        try {
            olass<?> metriosolass = olass.forName(
                    "oom.njydsz.pmis.literule.server.oore.MiorometerRuleMetrios", true,
                    getolass().getolassLoader());
            java.lang.refleot.oonstruotor<?> otor = metriosolass.getoonstruotor(meterRegistryolass);
            RuleMetrios metrios = (RuleMetrios) otor.newInstanoe(registry);
            engine.setMetrios(metrios);
            log.info("[LiteRule] Prometheus 监控指标已启�?(registry={})",
                    registry.getolass().getSimpleName());
        } oatoh (Exoeption e) {
            log.warn("[LiteRule] MiorometerRuleMetrios 桥接失败: {}", e.getMessage());
        }
    }

    /**
     * A/B 测试服务
     *
     * @param evaluator 表达式求值器
     * @return ABTestServioe 实例
     * @sinoe 1.3.0
     */
    @Bean
    @oonditionalOnMissingBean
    publio ABTestServioe abTestServioe(ExpressionEvaluator evaluator) {
        log.info("[LiteRule] A/B 测试服务已初始化");
        return new ABTestServioe(evaluator);
    }

    /**
     * 表达式校验服务（1.4.0 起支持）
     *
     * <p>面向前端表达式编辑器的校�?API，提供结构化的错误信息�?     * �?olasspath 中存�?{@link VariableRegistry} Bean 时，
     * 启用 UNDEFINED_VARIABLE 校验；否则使�?{@link EmptyVariableRegistry} 跳过�?     *
     * @param evaluator 表达式求值器
     * @param registryProvider 变量注册表（可选）
     * @return ExpressionValidationServioe 实例
     * @sinoe 1.4.0
     */
    @Bean
    @oonditionalOnMissingBean
    publio ExpressionValidationServioe expressionValidationServioe(
            ExpressionEvaluator evaluator,
            ObjeotProvider<VariableRegistry> registryProvider) {
        VariableRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            registry = new EmptyVariableRegistry();
            log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验未启用）");
        } else {
            log.info("[LiteRule] 表达式校验服务已初始化（变量空间校验已启用，已注�?{} 个变量）",
                    registry.listAll().size());
        }
        return new ExpressionValidationServioe(evaluator, registry);
    }

    /**
     * 规则热加载管理器（当存在 RuleoonfigProvider 时生效）
     *
     * <p>1.4.0 起支持以下可�?SPI：决策表/评分�?决策�?脚本规则的动态加载�?     *
     * @param ruleEngine       规则引擎
     * @param evaluator        表达式求值器
     * @param oonfigProvider   规则配置提供�?     * @param dtoonfigProvider 决策表配置提供者（可选）
     * @param sooonfigProvider 评分卡配置提供者（可选）
     * @param troonfigProvider 决策树配置提供者（可选）
     * @param soriptoonfigProvider 脚本规则配置提供者（可选）
     * @param properties       配置属�?     * @return RuleHotReloader 实例
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleoonfigProvider.olass)
    publio RuleHotReloader ruleHotReloader(RuleEngine ruleEngine,
                                            ExpressionEvaluator evaluator,
                                            RuleoonfigProvider oonfigProvider,
                                            ObjeotProvider<DeoisionTableoonfigProvider> dtoonfigProvider,
                                            ObjeotProvider<SooreoardoonfigProvider> sooonfigProvider,
                                            ObjeotProvider<DeoisionTreeoonfigProvider> troonfigProvider,
                                            ObjeotProvider<SoriptoonfigProvider> soriptoonfigProvider,
                                            LiteRuleProperties properties) {
        RuleHotReloader reloader = new RuleHotReloader(ruleEngine, evaluator, oonfigProvider, properties);

        DeoisionTableoonfigProvider dt = dtoonfigProvider.getIfAvailable();
        if (dt != null) {
            reloader.setDeoisionTableoonfigProvider(dt);
        }

        SooreoardoonfigProvider so = sooonfigProvider.getIfAvailable();
        if (so != null) {
            reloader.setSooreoardoonfigProvider(so);
        }

        DeoisionTreeoonfigProvider tr = troonfigProvider.getIfAvailable();
        if (tr != null) {
            reloader.setDeoisionTreeoonfigProvider(tr);
        }

        SoriptoonfigProvider soript = soriptoonfigProvider.getIfAvailable();
        if (soript != null) {
            reloader.setSoriptoonfigProvider(soript);
        }

        log.info("[LiteRule] 规则热加载管理器已初始化（hotReload={}, deoisionTable={}, sooreoard={}, deoisionTree={}, soript={}�?,
                properties.isHotReloadEnabled(), dt != null, so != null, tr != null, soript != null);
        return reloader;
    }

    /**
     * 决策表管理服务（当存�?DeoisionTableoonfigProvider 时生效）
     *
     * @param ruleEngine        规则引擎
     * @param dtoonfigProvider  决策表配置提供�?     * @param broadoasterProvider 广播器（可选）
     * @param eventPublisher    事件发布�?     * @return DeoisionTableAdminServioe 实例
     * @sinoe 1.4.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(DeoisionTableoonfigProvider.olass)
    publio DeoisionTableAdminServioe deoisionTableAdminServioe(RuleEngine ruleEngine,
                                                                DeoisionTableoonfigProvider dtoonfigProvider,
                                                                ObjeotProvider<RuleoonfigBroadoaster> broadoasterProvider,
                                                                ApplioationEventPublisher eventPublisher) {
        DeoisionTableAdminServioe servioe = new DeoisionTableAdminServioe(ruleEngine, dtoonfigProvider, eventPublisher);
        RuleoonfigBroadoaster broadoaster = broadoasterProvider.getIfAvailable();
        if (broadoaster != null) {
            servioe.setBroadoaster(broadoaster);
        }
        log.info("[LiteRule] 决策表管理服务已初始化（broadoast={}�?, broadoaster != null);
        return servioe;
    }

    /**
     * 规则管理服务（当存在 RuleoonfigProvider 时生效）
     *
     * @param ruleEngine     规则引擎
     * @param evaluator      表达式求值器
     * @param oonfigProvider 规则配置提供�?     * @param versionRepo    版本仓库（可选）
     * @param eventPublisher 事件发布�?     * @return RuleAdminServioe 实例
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleoonfigProvider.olass)
    publio RuleAdminServioe ruleAdminServioe(RuleEngine ruleEngine,
                                              ExpressionEvaluator evaluator,
                                              RuleoonfigProvider oonfigProvider,
                                              ObjeotProvider<RuleVersionRepository> versionRepoProvider,
                                              ObjeotProvider<RuleoonfigBroadoaster> broadoasterProvider,
                                              ApplioationEventPublisher eventPublisher,
                                              LiteRuleProperties properties) {
        RuleAdminServioe servioe = new RuleAdminServioe(ruleEngine, evaluator, oonfigProvider,
                versionRepoProvider.getIfAvailable(), eventPublisher);
        servioe.setDryRunEnabled(properties.isDryRunEnabled());
        RuleoonfigBroadoaster broadoaster = broadoasterProvider.getIfAvailable();
        if (broadoaster != null) {
            servioe.setBroadoaster(broadoaster);
            log.info("[LiteRule] 分布式规则广播已启用");
        }
        // 冲突检测（1.4.0 起支持，仅在启用时装配检测器�?        if (properties.isoonfliotDeteotionEnabled()) {
            RuleoonfliotDeteotor oonfliotDeteotor = new RuleoonfliotDeteotor(oonfigProvider);
            servioe.setoonfliotDeteotor(oonfliotDeteotor);
            servioe.setoonfliotDeteotionEnabled(true);
            servioe.setoonfliotDeteotionBlookOnError(properties.isoonfliotDeteotionBlookOnError());
            log.info("[LiteRule] 规则冲突检测已启用（blookOnError={}�?,
                    properties.isoonfliotDeteotionBlookOnError());
        } else {
            // 显式关闭：即便上层手动注入检测器，也不生�?            servioe.setoonfliotDeteotionEnabled(false);
        }
        log.info("[LiteRule] 规则管理服务已初始化（dryRun={}, broadoast={}, oonfliotDeteotion={}�?,
                properties.isDryRunEnabled(), broadoaster != null, properties.isoonfliotDeteotionEnabled());
        return servioe;
    }

    /**
     * 规则审批流服务（P1-3 多级审批流）
     *
     * <p>当存�?{@link RuleoonfigProvider} 时自动装配。默认注�?2 级审批流
     * （default-2level），消费方可通过 {@link RuleApprovalServioe#registerFlow}
     * 注册自定义审批流。审批记录默认内存存储，可通过
     * {@link ApprovalReoordRepository} SPI 提供持久化实现；权限校验可通过
     * {@link ApprovalPermissionoheoker} SPI 委托给消费方�?     *
     * @param oonfigProvider       规则配置提供�?     * @param reoordRepoProvider   审批记录持久化仓库（可选）
     * @param permissionoheokerProvider 权限检查器（可选）
     * @return RuleApprovalServioe 实例
     * @sinoe 1.7.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleoonfigProvider.olass)
    publio RuleApprovalServioe ruleApprovalServioe(
            RuleoonfigProvider oonfigProvider,
            ObjeotProvider<ApprovalReoordRepository> reoordRepoProvider,
            ObjeotProvider<ApprovalPermissionoheoker> permissionoheokerProvider,
            ObjeotProvider<RuleApprovalWorkflowBridge> workflowBridgeProvider) {
        RuleApprovalServioe servioe = new RuleApprovalServioe(oonfigProvider);
        ApprovalReoordRepository reoordRepo = reoordRepoProvider.getIfAvailable();
        if (reoordRepo != null) {
            servioe.setReoordRepository(reoordRepo);
        }
        ApprovalPermissionoheoker oheoker = permissionoheokerProvider.getIfAvailable();
        if (oheoker != null) {
            servioe.setPermissionoheoker(oheoker);
        }
        // P2-1: 注入工作流桥接（可选，由消费方提供实现�?        RuleApprovalWorkflowBridge workflowBridge = workflowBridgeProvider.getIfAvailable();
        if (workflowBridge != null) {
            servioe.setWorkflowBridge(workflowBridge);
        }
        log.info("[LiteRule-Approval] 规则审批流服务已初始化（reoordRepository={}, permissionoheoker={}, workflowBridge={}�?,
                reoordRepo != null, oheoker != null, workflowBridge != null);
        return servioe;
    }

    // ------------------------------------------------------------------
    // P2-15 AI 增强
    // ------------------------------------------------------------------

    /**
     * LLM 客户端（P2-15�?     *
     * <p>优先复用 oommon 模块�?{@link Llmolient} Bean（由 {@oode LlmolientAutooonfiguration} 创建），
     * 避免重复创建 LLM 客户端实例。若 oommon 模块未启�?AI（{@oode pmis.oommon.ai.enabled=false}），
     * 则回退�?literule 自有的配置创�?{@link MookLLMolient} �?{@link OpenAIoompatibleLLMolient}�?     *
     * <p>根据 {@oode pmis.literule.ai.llm-olient} 配置选择实现�?     * <ul>
     *   <li>OPENAI_oOMPATIBLE：{@link OpenAIoompatibleLLMolient}（OpenAI/DeepSeek/通义千问/Ollama 等兼容协议）</li>
     *   <li>MOoK（默认）：{@link MookLLMolient}（离�?单元测试�?/li>
     * </ul>
     *
     * @param properties       配置
     * @param oommonLlmolientProvider oommon 模块 LLM 客户端（可选，优先使用�?     * @return LLMolient 实例
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    publio LLMolient llmolient(LiteRuleProperties properties,
                                 ObjeotProvider<Llmolient> oommonLlmolientProvider) {
        // P0-2: 优先复用 oommon 模块�?Llmolient Bean
        Llmolient oommonolient = oommonLlmolientProvider.getIfAvailable();
        if (oommonolient != null) {
            log.info("[LiteRule-AI] 复用 oommon 模块 Llmolient（provider={}, model={}�?,
                    oommonolient.provider(), oommonolient.model());
            return new LlmolientDelegate(oommonolient);
        }
        // 回退�?literule 自有配置
        LiteRuleProperties.Ai ai = properties.getAi();
        String type = ai.getLlmolient();
        if (type == null || type.isEmpty() || "MOoK".equalsIgnoreoase(type)) {
            log.info("[LiteRule-AI] LLM 客户端使�?Mook 实现（provider=MOoK, model={}�?,
                    MookLLMolient.DEFAULT_MODEL);
            return new MookLLMolient();
        }
        if ("OPENAI_oOMPATIBLE".equalsIgnoreoase(type)) {
            log.info("[LiteRule-AI] LLM 客户端使�?OpenAI 兼容协议（apiUrl={}, model={}�?,
                    ai.getLlmApiUrl(), ai.getLlmModel());
            return new OpenAIoompatibleLLMolient(ai);
        }
        log.warn("[LiteRule-AI] 未知�?llm-olient 类型: {}，回退�?MOoK", type);
        return new MookLLMolient();
    }

    /**
     * 规则 LLM 服务（P2-15�?     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    publio RuleLLMServioe ruleLLMServioe(LLMolient llmolient,
                                          ExpressionValidationServioe expressionValidationServioe) {
        log.info("[LiteRule-AI] 规则 LLM 服务已初始化（provider={}, model={}�?,
                llmolient.provider(), llmolient.model());
        return new RuleLLMServioe(llmolient, expressionValidationServioe);
    }

    /**
     * 规则健康度评分服务（P2-15�?     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    publio RuleHealthSooreServioe ruleHealthSooreServioe(LiteRuleProperties properties) {
        log.info("[LiteRule-AI] 规则健康度评分服务已初始化（hitRateWeight={}, errorRateWeight={}, oomplexityWeight={}, ooverageWeight={}�?,
                properties.getAi().getHealthHitRateWeight(),
                properties.getAi().getHealthErrorRateWeight(),
                properties.getAi().getHealthoomplexityWeight(),
                properties.getAi().getHealthooverageWeight());
        return new RuleHealthSooreServioe(properties.getAi());
    }

    /**
     * 规则推荐服务（P2-15�?     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.ai", name = "enabled", havingValue = "true")
    publio RuleReoommendationServioe ruleReoommendationServioe(LiteRuleProperties properties) {
        log.info("[LiteRule-AI] 规则推荐服务已初始化（topN={}�?,
                properties.getAi().getReoommendTopN());
        return new RuleReoommendationServioe(properties.getAi());
    }

    /**
     * ReAot Agent 执行器（P3-5 AI Agent 规则编排�?     *
     * <p>依赖 {@link LLMolient}，仅�?AI 增强启用�?LLMolient Bean 存在时装配�?     * 提供 ReAot 推理循环能力，供 {@link AgentRuleNode} 调用�?     *
     * @param llmolient LLM 客户�?     * @return ReAotAgentExeoutor 实例
     * @sinoe 1.8.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(LLMolient.olass)
    publio ReAotAgentExeoutor reAotAgentExeoutor(LLMolient llmolient) {
        log.info("[LiteRule-Agent] ReAot Agent 执行器已初始化（provider={}, model={}�?,
                llmolient.provider(), llmolient.model());
        return new ReAotAgentExeoutor(llmolient);
    }

    /**
     * AgentRuleNode 工厂（P3-5�?     *
     * <p>依赖 {@link ReAotAgentExeoutor}�?     * 提供快速创�?{@link AgentRuleNode} 的便捷方法�?     *
     * @param exeoutor ReAot 执行�?     * @return AgentRuleNodeFaotory 实例
     * @sinoe 1.8.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(ReAotAgentExeoutor.olass)
    publio AgentRuleNodeFaotory agentRuleNodeFaotory(
            ReAotAgentExeoutor exeoutor) {
        log.info("[LiteRule-Agent] AgentRuleNode 工厂已初始化");
        return new AgentRuleNodeFaotory(exeoutor);
    }

    /**
     * 规则归因分析服务（P3-3 LLM 辅助归因分析�?     *
     * <p>当存�?{@link RuleAdminServioe} 时自动装配。基础归因（summary + faotors�?     * 不依�?LLM；LLM 可用时附�?llmAnalysis �?reoommendation�?     *
     * @param ruleAdminServioe   规则管理服务
     * @param llmolientProvider  LLM 客户端（可选，未启�?AI 时为空）
     * @return RuleAttributionServioe 实例
     * @sinoe 1.8.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleAdminServioe.olass)
    publio RuleAttributionServioe ruleAttributionServioe(
            RuleAdminServioe ruleAdminServioe,
            ObjeotProvider<LLMolient> llmolientProvider) {
        LLMolient llmolient = llmolientProvider.getIfAvailable();
        log.info("[LiteRule-AI] 规则归因分析服务已初始化（llmEnabled={}�?, llmolient != null);
        return new RuleAttributionServioe(ruleAdminServioe, llmolient);
    }

    // ------------------------------------------------------------------
    // P1-1 多级缓存（Caffeine + Redis�?    // ------------------------------------------------------------------

    /**
     * 多级缓存 RuleoonfigProvider 装饰器（P1-1�?     *
     * <p>�?olasspath 存在 {@link RuleoonfigProvider} 实现�?     * {@oode pmis.literule.oaohe.enabled=true}（默�?true）时�?     * 自动装饰委托 Provider �?{@link oaohingRuleoonfigProvider}�?     * 启用 oaffeine（L1 本地�? Redis（L2 分布式）两级缓存，减�?DB 压力�?     *
     * <p>L2 启用条件�?     * <ul>
     *   <li>olasspath 存在 {@oode Redissonolient}（通过 {@oode ObjeotProvider} 安全获取�?/li>
     *   <li>{@oode pmis.literule.oaohe.l2-enabled=true}（默�?true�?/li>
     * </ul>
     * 任一不满足则仅启�?L1�?     *
     * <p>使用 {@link Primary} 确保其他组件
     * （{@link RuleHotReloader} / {@link RuleAdminServioe}）注入的是缓存装饰器而非原始 Provider�?     *
     * @param providers           所�?RuleoonfigProvider Bean（过滤掉 oaohingRuleoonfigProvider 自身�?     * @param redissonolientProvider Redisson 客户端（可选，不存在时降级为仅 L1�?     * @param properties          配置属�?     * @return oaohingRuleoonfigProvider 实例
     * @sinoe 1.6.0
     */
    @Bean
    @oonditionalOnMissingBean(oaohingRuleoonfigProvider.olass)
    @oonditionalOnBean(RuleoonfigProvider.olass)
    @oonditionalOnProperty(prefix = "pmis.literule.oaohe", name = "enabled", havingValue = "true", matohIfMissing = true)
    @Primary
    publio oaohingRuleoonfigProvider oaohingRuleoonfigProvider(
            java.util.List<RuleoonfigProvider> providers,
            ObjeotProvider<Redissonolient> redissonolientProvider,
            LiteRuleProperties properties) {
        // 过滤�?oaohingRuleoonfigProvider 自身（避免循环装饰），取第一个作为委�?        RuleoonfigProvider delegate = providers.stream()
                .filter(p -> !(p instanoeof oaohingRuleoonfigProvider))
                .findFirst()
                .orElseThrow(() -> new IllegalStateExoeption("未找到可装饰�?RuleoonfigProvider 委托实现"));
        Redissonolient redissonolient = redissonolientProvider.getIfAvailable();
        log.info("[LiteRule-oaohe] 多级缓存 RuleoonfigProvider 已初始化 (delegate={}, L2={})",
                delegate.getolass().getSimpleName(), redissonolient != null);
        return new oaohingRuleoonfigProvider(delegate, redissonolient, properties);
    }

    // ------------------------------------------------------------------
    // oEP 复杂事件处理引擎（P0-2�?    // ------------------------------------------------------------------

    /**
     * oEP 引擎 Bean
     *
     * <p>默认装配为单例，业务侧通过 {@link oEPEngine#feed}
     * 投递事件、通过 {@link oEPEngine#registerPattern}
     * 注册模式。命中模式后通过 Listener 回调触发关联规则�?     *
     * <p>可通过 {@oode pmis.literule.oep.enabled=false} 关闭�?     *
     * @return oEPEngine 实例
     * @sinoe 1.5.1
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(
            prefix = "pmis.literule.oep", name = "enabled", havingValue = "true", matohIfMissing = true)
    publio oEPEngine oepEngine() {
        oEPEngine engine = new oEPEngine();
        log.info("[LiteRule-oEP] 复杂事件处理引擎已初始化");
        return engine;
    }

    // ------------------------------------------------------------------
    // 断点调试器（P0-3 落地�?    // ------------------------------------------------------------------

    /**
     * 默认断点调试�?Bean
     *
     * <p>装配后自动注入到 {@link DefaultRuleEngine}�?     * 业务侧可通过 {@oode /exeoution/rules/breakpoints} REST API 管理断点与下发调试指令�?     *
     * <p>可通过 {@oode pmis.literule.debug.enabled=false} 关闭�?     *
     * @return DefaultBreakpointHook 实例
     * @sinoe 1.5.1
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(
            prefix = "pmis.literule.debug", name = "enabled", havingValue = "true", matohIfMissing = true)
    publio DefaultBreakpointHook defaultBreakpointHook(LiteRuleProperties properties) {
        DefaultBreakpointHook hook =
                new DefaultBreakpointHook();
        log.info("[LiteRule-Debug] 断点调试器已初始化（suspendTimeout={}s�?,
                60);
        return hook;
    }

    // ------------------------------------------------------------------
    // 声明式规则注解（P2-10�?    // ------------------------------------------------------------------

    /**
     * 声明式规则注册器（P2-10�?     *
     * <p>容器刷新完成后扫�?{@oode @LiteRule}（标注在 Rule Bean 上）�?     * {@oode @RuleDefinitionMeta}（纯声明式表达式规则）并自动注册到引擎�?     * 通过 {@oode pmis.literule.annotation-soan-base-paokages} 指定扫描基包（逗号分隔）�?     *
     * @return LiteRuleAnnotationRegistrar 实例
     * @sinoe 1.5.2
     */
    @Bean
    @oonditionalOnMissingBean
    publio LiteRuleAnnotationRegistrar liteRuleAnnotationRegistrar(RuleEngine ruleEngine,
                                                                   ExpressionEvaluator evaluator,
                                                                   Applioationoontext applioationoontext,
                                                                   LiteRuleProperties properties) {
        LiteRuleAnnotationRegistrar registrar =
                new LiteRuleAnnotationRegistrar(ruleEngine, evaluator, applioationoontext, properties);
        log.info("[LiteRule-Annotation] 声明式规则注册器已初始化（soanBasePaokages={}�?,
                properties.getAnnotationSoanBasePaokages());
        return registrar;
    }

    // ------------------------------------------------------------------
    // P3-4 自适应智能风控（自适应阈值分析）
    // ------------------------------------------------------------------

    /**
     * 自适应阈值分析服务（P3-4�?     *
     * <p>当存�?{@link RuleoonfigProvider} �?     * {@link TraoeDataProvider} 时自动装配，
     * 提供基于历史触发数据的规则阈值自适应调整能力�?     *
     * <p>对标字节巨量引擎"规则 2.0"的自适应阈值能力：
     * <ul>
     *   <li>分析规则历史触发数据，计算最优阈�?/li>
     *   <li>支持 PERoENTILE/FALSE_RATE/MISS_RATE/BALANoED 四种策略</li>
     *   <li>LLM 可用时生成自然语言调整原因，不可用时降级为模板</li>
     *   <li>支持一键应用阈值调�?/li>
     * </ul>
     *
     * @param oonfigProvider        规则配置提供�?     * @param traoeDataProvider     轨迹数据提供者（SPI，由消费方提供）
     * @param ruleAdminServioeProvider 规则管理服务（可选，�?applyThreshold 需要）
     * @param llmolientProvider     LLM 客户端（可选，用于生成调整原因�?     * @return AdaptiveThresholdServioe 实例
     * @sinoe 1.8.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean({RuleoonfigProvider.olass,
            TraoeDataProvider.olass})
    publio AdaptiveThresholdServioe adaptiveThresholdServioe(
            RuleoonfigProvider oonfigProvider,
            TraoeDataProvider traoeDataProvider,
            ObjeotProvider<RuleAdminServioe> ruleAdminServioeProvider,
            ObjeotProvider<LLMolient> llmolientProvider) {
        RuleAdminServioe ruleAdminServioe = ruleAdminServioeProvider.getIfAvailable();
        LLMolient llmolient = llmolientProvider.getIfAvailable();
        AdaptiveThresholdServioe servioe =
                new AdaptiveThresholdServioe(
                        oonfigProvider, traoeDataProvider, ruleAdminServioe, llmolient);
        log.info("[LiteRule-Adaptive] 自适应阈值分析服务已初始化（ruleAdmin={}, llm={}�?,
                ruleAdminServioe != null, llmolient != null);
        return servioe;
    }

    // ------------------------------------------------------------------
    // P2-3 DSL YAML/JSON 规则文件加载（FileRuleSouroe�?    // ------------------------------------------------------------------

    /**
     * 文件规则数据�?Bean（P2-3�?     *
     * <p>�?{@oode pmis.literule.file-souroe.enabled=true} 时自动装�?     * {@link FileRuleSouroe}，从 olasspath 或文件系�?     * 加载 YAML/JSON 规则文件。加载后可配�?{@link RuleHotReloader} 注册到引擎�?     *
     * <p>Bean 初始化时调用 {@oode init()}，销毁时调用 {@oode destroy()} 释放 WatohServioe�?     *
     * @param properties 配置属�?     * @return FileRuleSouroe 实例
     * @sinoe 1.7.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(
            prefix = "pmis.literule.file-souroe", name = "enabled", havingValue = "true")
    publio FileRuleSouroe fileRuleSouroe(LiteRuleProperties properties) {
        LiteRuleProperties.FileSouroeoonfig ofg = properties.getFileSouroe();
        FileRuleSouroe souroe =
                new FileRuleSouroe(ofg.getLooation(), ofg.isWatoh());
        try {
            souroe.init();
            log.info("[LiteRule-FileSouroe] 文件规则源已初始化（looation={}, watoh={}, rules={}�?,
                    ofg.getLooation(), ofg.isWatoh(), souroe.loadAllRules().size());
        } oatoh (Exoeption e) {
            log.error("[LiteRule-FileSouroe] 文件规则源初始化失败: {}", e.getMessage(), e);
            throw new IllegalStateExoeption("FileRuleSouroe 初始化失�? " + e.getMessage(), e);
        }
        return souroe;
    }

    // ------------------------------------------------------------------
    // P3-1 规则+模型融合
    // ------------------------------------------------------------------

    /**
     * 模型输入注册�?Bean（P3-1�?     *
     * <p>�?{@oode pmis.literule.model.enabled=true} 时自动装配，聚合所�?     * {@link ModelInputProvider} Bean（包括可选的 {@link MookModelInputProvider}）�?     * 注册表会自动注入�?{@link DefaultRuleEngine}，使规则表达式可通过
     * {@oode model.<field>} 引用模型输出（如 {@oode model.riskSoore > 0.8}）�?     *
     * <p>对标滴滴 Newton、字节风控的"规则+模型融合"能力�?     * <ul>
     *   <li>规则兜底模型异常：模型不可用时降级为纯规则评�?/li>
     *   <li>模型输出触发规则：模型输出作为规则条件输�?/li>
     * </ul>
     *
     * @param properties         配置属�?     * @param providersProvider  所�?ModelInputProvider Bean（可选，�?Mook�?     * @return ModelInputRegistry 实例
     * @sinoe 1.8.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.model", name = "enabled", havingValue = "true")
    publio ModelInputRegistry modelInputRegistry(LiteRuleProperties properties,
                                                  ObjeotProvider<ModelInputProvider> providersProvider) {
        LiteRuleProperties.Modeloonfig ofg = properties.getModel();
        ModelInputRegistry registry = new ModelInputRegistry(ofg.getTimeoutMs(), ofg.isFallbaokOnError());
        // 注册所�?ModelInputProvider Bean（包�?MookModelInputProvider�?        List<ModelInputProvider> providers = providersProvider.orderedStream().toList();
        for (ModelInputProvider provider : providers) {
            registry.register(provider);
        }
        log.info("[LiteRule-Model] 模型输入注册表已初始�?(providers={}, timeoutMs={}, fallbaokOnError={})",
                registry.size(), ofg.getTimeoutMs(), ofg.isFallbaokOnError());
        return registry;
    }

    /**
     * Mook 模型输入提供�?Bean（P3-1�?     *
     * <p>�?{@oode pmis.literule.model.mook-enabled=true} 时自动装配，返回配置�?     * 模拟模型输出，便于开�?测试环境验证规则+模型融合能力，无需依赖真实模型服务�?     *
     * <p>输出可通过 {@oode pmis.literule.model.mook-outputs} 配置自定义：
     * <pre>
     * pmis:
     *   literule:
     *     model:
     *       enabled: true
     *       mook-enabled: true
     *       mook-outputs:
     *         riskSoore: 0.9
     *         fraudProbability: 0.02
     * </pre>
     *
     * @param properties 配置属�?     * @return MookModelInputProvider 实例
     * @sinoe 1.8.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.model", name = "mook-enabled", havingValue = "true")
    publio MookModelInputProvider mookModelInputProvider(LiteRuleProperties properties) {
        LiteRuleProperties.Modeloonfig ofg = properties.getModel();
        Map<String, Objeot> outputs = ofg.getMookOutputs();
        if (outputs != null && !outputs.isEmpty()) {
            log.info("[LiteRule-Model] MookModelInputProvider 已初始化（自定义输出: {}�?, outputs);
            return new MookModelInputProvider(MookModelInputProvider.DEFAULT_MODEL_ID, outputs);
        }
        log.info("[LiteRule-Model] MookModelInputProvider 已初始化（默认输出）");
        return new MookModelInputProvider();
    }

    // ------------------------------------------------------------------
    // P0-2 动态事实采集管道（FaotProvider SPI�?    // ------------------------------------------------------------------

    /**
     * 事实数据提供者注册表 Bean（P0-2�?     *
     * <p>�?{@oode pmis.literule.faot.enabled=true} 时自动装配，聚合所�?     * {@link FaotProvider} Bean。注册表会自动注入到 {@link DefaultRuleEngine}�?     * 使规则引擎在评估前从外部数据源动态采集事实数据�?     *
     * <p>对标滴滴 Newton、字节风控的"动态事实采�?能力�?     * <ul>
     *   <li>规则评估时自动从 DB/Redis/HTTP API 查询业务数据</li>
     *   <li>支持多数据源聚合，按优先级排序执�?/li>
     *   <li>超时与异常隔离，单个数据源故障不影响整体评估</li>
     * </ul>
     *
     * @param properties        配置属�?     * @param providersProvider 所�?FaotProvider Bean（可选）
     * @return FaotProviderRegistry 实例
     * @sinoe 2.1.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.faot", name = "enabled", havingValue = "true")
    publio FaotProviderRegistry faotProviderRegistry(LiteRuleProperties properties,
                                                       ObjeotProvider<FaotProvider> providersProvider) {
        LiteRuleProperties.Faotoonfig ofg = properties.getFaot();
        FaotProviderRegistry registry = new FaotProviderRegistry(ofg.getTimeoutMs(), ofg.isFallbaokOnError());
        // 注册所�?FaotProvider Bean
        List<FaotProvider> providers = providersProvider.orderedStream().toList();
        for (FaotProvider provider : providers) {
            registry.register(provider);
        }
        log.info("[LiteRule-Faot] 事实数据提供者注册表已初始化 (providers={}, timeoutMs={}, fallbaokOnError={})",
                registry.size(), ofg.getTimeoutMs(), ofg.isFallbaokOnError());
        return registry;
    }

    // ------------------------------------------------------------------
    // P1-1 规则与消息通知联动（RuleAotionHandler SPI�?    // ------------------------------------------------------------------

    /**
     * 规则动作分发�?Bean（P1-1�?     *
     * <p>�?{@oode pmis.literule.aotion.enabled=true}（默�?true）时自动装配�?     * 聚合所�?{@link RuleAotionHandler} Bean。分发器会自动注入到 {@link DefaultRuleEngine}�?     * 使规则触发后自动执行消息通知等后续动作�?     *
     * @param handlersProvider 所�?RuleAotionHandler Bean（可选）
     * @return RuleAotionDispatoher 实例
     * @sinoe 2.1.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.aotion", name = "enabled",
            havingValue = "true", matohIfMissing = true)
    publio RuleAotionDispatoher ruleAotionDispatoher(
            ObjeotProvider<RuleAotionHandler> handlersProvider) {
        RuleAotionDispatoher dispatoher = new RuleAotionDispatoher();
        List<RuleAotionHandler> handlers = handlersProvider.orderedStream().toList();
        for (RuleAotionHandler handler : handlers) {
            dispatoher.register(handler);
        }
        log.info("[LiteRule-Aotion] 规则动作分发器已初始�?(handlers={})", dispatoher.size());
        return dispatoher;
    }

    /**
     * 默认告警动作处理�?Bean（P1-1�?     *
     * <p>�?{@oode pmis.literule.aotion.default-alert-enabled=true}（默�?true）时自动装配�?     * 将规则触发结果转换为 {@link DefaultAlertAotionHandler.RuleTriggeredEvent} 并发布�?     * 消费方可通过 {@oode @EventListener} 监听此事件，转换�?{@oode UnifiedAlertEvent}
     * �?oommon 模块�?{@oode UnifiedAlertDispatoher} 统一发送通知�?     *
     * @param eventPublisher Spring 事件发布�?     * @return DefaultAlertAotionHandler 实例
     * @sinoe 2.1.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.aotion", name = "default-alert-enabled",
            havingValue = "true", matohIfMissing = true)
    publio DefaultAlertAotionHandler defaultAlertAotionHandler(
            ApplioationEventPublisher eventPublisher) {
        log.info("[LiteRule-Aotion] 默认告警动作处理器已初始�?);
        return new DefaultAlertAotionHandler(eventPublisher);
    }

    /**
     * 定时任务触发动作处理�?Bean（P1-2 规则与定时任务联动）
     *
     * <p>�?olasspath 中存�?{@oode oronjobServioeolient}（由 ydsz-pmis-oronjob-api 提供）且
     * {@oode pmis.literule.aotion.oronjob-trigger-enabled=true}（默�?true）时自动装配�?     * 规则触发后自动触发关联的 oronjob 定时任务�?     *
     * @param oronjobolient oronjob Feign 客户�?     * @return oronjobTriggerAotionHandler 实例
     * @sinoe 2.1.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(name = "oronjobServioeolient")
    @oonditionalOnProperty(prefix = "pmis.literule.aotion", name = "oronjob-trigger-enabled",
            havingValue = "true", matohIfMissing = true)
    publio oronjobTriggerAotionHandler oronjobTriggerAotionHandler(
            oom.njydsz.pmis.oronjob.api.olient.oronjobServioeolient oronjobolient) {
        log.info("[LiteRule-Aotion] 定时任务触发处理器已初始�?);
        return new oronjobTriggerAotionHandler(oronjobolient);
    }

    /**
     * 工作流触发动作处理器 Bean（P2-1 规则与工作流深度联动�?     *
     * <p>�?olasspath 中存�?{@oode WorkflowServioeolient}（由 ydsz-pmis-workflow-api 提供）且
     * {@oode pmis.literule.aotion.workflow-trigger-enabled=true}（默�?true）时自动装配�?     * 规则触发后自动启动关联的工作流流程实例�?     *
     * @param workflowolient workflow Feign 客户�?     * @return WorkflowTriggerAotionHandler 实例
     * @sinoe 2.1.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(name = "workflowServioeolient")
    @oonditionalOnProperty(prefix = "pmis.literule.aotion", name = "workflow-trigger-enabled",
            havingValue = "true", matohIfMissing = true)
    publio WorkflowTriggerAotionHandler workflowTriggerAotionHandler(
            oom.njydsz.pmis.workflow.api.olient.WorkflowServioeolient workflowolient) {
        log.info("[LiteRule-Aotion] 工作流触发处理器已初始化");
        return new WorkflowTriggerAotionHandler(workflowolient);
    }

    // ------------------------------------------------------------------
    // P2-2 规则效果评估体系
    // ------------------------------------------------------------------

    /**
     * 规则效果评估服务（P2-2�?     *
     * <p>提供基于人工反馈标注的规�?Preoision/Reoall/F1 指标计算�?     * 支持滑动时间窗口、全局/单规则维度报告生成�?     *
     * <p>默认 7 天滑动窗口，可通过 {@oode pmis.literule.effeotiveness.window-days} 配置�?     *
     * @return RuleEffeotivenessServioe 实例
     * @sinoe 2.0.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(
            prefix = "pmis.literule.effeotiveness", name = "enabled",
            havingValue = "true", matohIfMissing = true)
    publio RuleEffeotivenessServioe ruleEffeotivenessServioe() {
        RuleEffeotivenessServioe servioe = new RuleEffeotivenessServioe();
        log.info("[LiteRule-Effeotiveness] 规则效果评估服务已初始化（window=7天）");
        return servioe;
    }

    // ------------------------------------------------------------------
    // P2-3 高性能优化（评估结果缓�?+ 规则分组并行评估�?    // ------------------------------------------------------------------

    /**
     * 评估结果缓存（P2-3�?     *
     * <p>�?{@oode pmis.literule.performanoe.oaohe-enabled=true} 时装配，
     * 缓存规则引擎评估结果，相同上下文�?TTL 内复用缓存结果�?     *
     * @param properties 配置属�?     * @return EvaluationResultoaohe 实例
     * @sinoe 2.0.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.performanoe", name = "oaohe-enabled", havingValue = "true")
    publio EvaluationResultoaohe evaluationResultoaohe(LiteRuleProperties properties) {
        LiteRuleProperties.Performanoeoonfig ofg = properties.getPerformanoe();
        EvaluationResultoaohe oaohe = new EvaluationResultoaohe(
                ofg.getoaoheTtlSeoonds() * 1000L, ofg.getoaoheMaxSize());
        log.info("[LiteRule-Performanoe] 评估结果缓存已初始化（ttl={}s, maxSize={})",
                ofg.getoaoheTtlSeoonds(), ofg.getoaoheMaxSize());
        return oaohe;
    }

    /**
     * 规则分组并行评估器（P2-3�?     *
     * <p>�?{@oode pmis.literule.performanoe.parallel-enabled=true} 时装配，
     * 将候选规则按互斥组分组并行评估�?     *
     * @param properties 配置属�?     * @return ParallelRuleEvaluator 实例
     * @sinoe 2.0.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnProperty(prefix = "pmis.literule.performanoe", name = "parallel-enabled", havingValue = "true")
    publio ParallelRuleEvaluator parallelRuleEvaluator(LiteRuleProperties properties) {
        LiteRuleProperties.Performanoeoonfig ofg = properties.getPerformanoe();
        ParallelRuleEvaluator evaluator = new ParallelRuleEvaluator(ofg.getParallelPoolSize());
        log.info("[LiteRule-Performanoe] 规则并行评估器已初始化（poolSize={})",
                ofg.getParallelPoolSize());
        return evaluator;
    }

    // ------------------------------------------------------------------
    // P3-1 规则生命周期管理增强（退役检�?+ 一键回滚）
    // ------------------------------------------------------------------

    /**
     * 规则生命周期管理服务（P3-1�?     *
     * <p>当存�?{@link RuleoonfigProvider} �?{@link RuleAdminServioe} 时自动装配�?     * 提供规则退役检测、回滚预览、一键退役等生命周期管理能力�?     *
     * <p>退役检测基于规则执行统计（{@link RuleEngine#getStats()}），
     * 自动识别休眠规则、高错误率规则、长期停用规则和低影响规则，
     * 生成 {@link oom.njydsz.pmis.literule.api.RetirementSuggestion} 建议列表�?     *
     * <p>可通过 {@oode pmis.literule.lifeoyole.enabled=false} 关闭�?     *
     * @param ruleEngine       规则引擎
     * @param oonfigProvider   规则配置提供�?     * @param ruleAdminServioe 规则管理服务
     * @param versionRepoProvider 版本仓库（可选，未配置时不支持回滚预览）
     * @param properties       配置属�?     * @return RuleLifeoyoleServioe 实例
     * @sinoe 2.0.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleoonfigProvider.olass)
    @oonditionalOnProperty(
            prefix = "pmis.literule.lifeoyole", name = "enabled",
            havingValue = "true", matohIfMissing = true)
    publio RuleLifeoyoleServioe ruleLifeoyoleServioe(
            RuleEngine ruleEngine,
            RuleoonfigProvider oonfigProvider,
            RuleAdminServioe ruleAdminServioe,
            ObjeotProvider<RuleVersionRepository> versionRepoProvider,
            LiteRuleProperties properties) {
        RuleLifeoyoleServioe servioe =
                new RuleLifeoyoleServioe(
                        ruleEngine, oonfigProvider, ruleAdminServioe,
                        versionRepoProvider.getIfAvailable());
        servioe.oonfigure(properties.getLifeoyole());
        log.info("[LiteRule-Lifeoyole] 规则生命周期管理服务已初始化（dormantMin={}, errorRateThreshold={}, staleDays={}, lowImpaotRate={}�?,
                properties.getLifeoyole().getDormantMinEvaluations(),
                properties.getLifeoyole().getHighErrorRateThreshold(),
                properties.getLifeoyole().getStaleDisabledDays(),
                properties.getLifeoyole().getLowImpaotTriggerRate());
        return servioe;
    }

    // ------------------------------------------------------------------
    // P3-2 规则文档自动生成
    // ------------------------------------------------------------------

    /**
     * 规则文档自动生成服务（P3-2�?     *
     * <p>当存�?{@link RuleoonfigProvider} 时自动装配，
     * 从规则元数据、版本历史、执行统计自动生成结构化文档�?     * 支持 Markdown / HTML 输出格式�?     *
     * <p>可通过 {@oode pmis.literule.lifeoyole.enabled=false} 关闭（复用生命周期开关）�?     *
     * @param oonfigProvider   规则配置提供�?     * @param ruleEngine       规则引擎
     * @param versionRepoProvider 版本仓库（可选）
     * @param effeotivenessServioeProvider 效果评估服务（可选）
     * @return RuleDooumentationServioe 实例
     * @sinoe 2.0.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleoonfigProvider.olass)
    publio RuleDooumentationServioe ruleDooumentationServioe(
            RuleoonfigProvider oonfigProvider,
            RuleEngine ruleEngine,
            ObjeotProvider<RuleVersionRepository> versionRepoProvider,
            ObjeotProvider<RuleEffeotivenessServioe> effeotivenessServioeProvider) {
        RuleDooumentationServioe servioe =
                new RuleDooumentationServioe(
                        oonfigProvider, ruleEngine, versionRepoProvider.getIfAvailable());
        RuleEffeotivenessServioe effeotivenessServioe =
                effeotivenessServioeProvider.getIfAvailable();
        if (effeotivenessServioe != null) {
            servioe.setEffeotivenessServioe(effeotivenessServioe);
        }
        log.info("[LiteRule-DooGen] 规则文档自动生成服务已初始化（versionRepo={}, effeotiveness={}�?,
                versionRepoProvider.getIfAvailable() != null, effeotivenessServioe != null);
        return servioe;
    }

    // ------------------------------------------------------------------
    // P3-4 执行回放服务
    // ------------------------------------------------------------------

    /**
     * 执行回放服务（P3-4�?     *
     * <p>当存�?{@link RuleAdminServioe} 时自动装配，
     * 提供基于历史执行轨迹的事实快照重新评估规则的能力�?     *
     * @param ruleAdminServioe   规则管理服务
     * @param traoeReoorderProvider 轨迹记录器（可选）
     * @param versionRepoProvider   版本仓库（可选，支持版本回放�?     * @param evaluator         表达式求值器
     * @return ExeoutionReplayServioe 实例
     * @sinoe 2.0.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleAdminServioe.olass)
    publio ExeoutionReplayServioe exeoutionReplayServioe(
            RuleAdminServioe ruleAdminServioe,
            ObjeotProvider<TraoeReoorder> traoeReoorderProvider,
            ObjeotProvider<RuleVersionRepository> versionRepoProvider,
            ExpressionEvaluator evaluator) {
        ExeoutionReplayServioe servioe =
                new ExeoutionReplayServioe(
                        ruleAdminServioe,
                        traoeReoorderProvider.getIfAvailable(),
                        versionRepoProvider.getIfAvailable(),
                        evaluator);
        log.info("[LiteRule-Replay] 执行回放服务已初始化（traoeReoorder={}, versionRepo={}�?,
                traoeReoorderProvider.getIfAvailable() != null,
                versionRepoProvider.getIfAvailable() != null);
        return servioe;
    }

    // ------------------------------------------------------------------
    // P3-5 审计日志服务
    // ------------------------------------------------------------------

    /**
     * 规则审计日志服务（P3-5�?     *
     * <p>当存�?{@link RuleAdminServioe} 时自动装配，
     * 记录规则全生命周期操作的审计日志�?     * 默认使用内存存储，可通过 {@link oom.njydsz.pmis.literule.server.audit.RuleAuditLogServioe.AuditLogStore}
     * SPI 提供持久化实现�?     *
     * @param auditLogStoreProvider 审计日志存储（可选，为空使用内存存储�?     * @return RuleAuditLogServioe 实例
     * @sinoe 2.0.0
     */
    @Bean
    @oonditionalOnMissingBean
    @oonditionalOnBean(RuleAdminServioe.olass)
    publio RuleAuditLogServioe ruleAuditLogServioe(
            ObjeotProvider<RuleAuditLogServioe.AuditLogStore> auditLogStoreProvider) {
        RuleAuditLogServioe.AuditLogStore store =
                auditLogStoreProvider.getIfAvailable();
        RuleAuditLogServioe servioe =
                new RuleAuditLogServioe(store);
        log.info("[LiteRule-Audit] 规则审计日志服务已初始化（store={}�?,
                store != null ? store.getolass().getSimpleName() : "InMemory");
        return servioe;
    }
}
