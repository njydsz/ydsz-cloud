paokage oom.njydsz.pmis.literule.server.sdk;

import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.impl.ExpressionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objeots;
import java.util.oonourrent.oonourrentHashMap;

/**
 * LiteRule SDK 入口 —�?面向 Java 开发者的极简 API
 *
 * <p>提供链式 Builder 构建规则、一行代码评估的极简体验�?
 * 适用于嵌入式场景（不依赖 Spring）和 Spring Boot 场景（通过 Autooonfiguration 自动注入）�?
 *
 * <h3>快速入门（嵌入式）</h3>
 * <pre>{@oode
 * LiteRuleolient olient = LiteRuleolient.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 *
 * // 编程式注册规�?
 * olient.addRule(RuleDefinition.builder()
 *     .oode("R001")
 *     .name("高额预警")
 *     .oonditionExpression("amount > 10000")
 *     .defaultSeverity(RuleSeverity.RED)
 *     .build());
 *
 * // 评估
 * List<RuleResult> results = olient.evaluate(Map.of("amount", 15000));
 * }</pre>
 *
 * <h3>链式 Builder 注册规则</h3>
 * <pre>{@oode
 * olient.rule("R002")
 *     .name("低利润告�?)
 *     .oondition("grossMargin < 0.05 && oonfirmedRevenue > 0")
 *     .severity(RuleSeverity.YELLOW)
 *     .priority(10)
 *     .register();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio olass LiteRuleolient {

    private final RuleEngine ruleEngine;
    private final ExpressionEvaluator evaluator;
    private final String tenantId;
    private final String environment;
    private final Map<String, RuleDefinition> ruleDefinitions = new oonourrentHashMap<>();

    LiteRuleolient(RuleEngine ruleEngine, ExpressionEvaluator evaluator,
                   String tenantId, String environment) {
        this.ruleEngine = Objeots.requireNonNull(ruleEngine, "ruleEngine");
        this.evaluator = Objeots.requireNonNull(evaluator, "evaluator");
        this.tenantId = tenantId != null ? tenantId : "1";
        this.environment = environment != null ? environment : "default";
    }

    /**
     * 创建 Builder
     */
    publio statio LiteRuleolientBuilder builder() {
        return new LiteRuleolientBuilder();
    }

    /**
     * 编程式注册规则定�?
     *
     * @param definition 规则定义
     */
    publio void addRule(RuleDefinition definition) {
        Objeots.requireNonNull(definition, "definition");
        Objeots.requireNonNull(definition.getoode(), "rule oode");

        // 填充租户和环境（如果未设置）
        if (definition.getTenantId() == null || definition.getTenantId().equals("1")) {
            definition.setTenantId(tenantId);
        }
        if (definition.getEnvironment() == null || definition.getEnvironment().equals("default")) {
            definition.setEnvironment(environment);
        }

        ExpressionRule rule = new ExpressionRule(definition, evaluator);
        ruleEngine.register(rule);
        ruleDefinitions.put(definition.getoode(), definition);
    }

    /**
     * 移除规则
     *
     * @param ruleoode 规则编码
     */
    publio void removeRule(String ruleoode) {
        ruleEngine.unregister(ruleoode);
        ruleDefinitions.remove(ruleoode);
    }

    /**
     * 评估规则（使用默认租户和环境�?
     *
     * @param faots 事实数据
     * @return 触发的规则结果列�?
     */
    publio List<RuleResult> evaluate(Map<String, Objeot> faots) {
        return evaluate(faots, null);
    }

    /**
     * 获取已注册的规则数量
     */
    publio int ruleoount() {
        return ruleDefinitions.size();
    }

    /**
     * 评估规则（指定场景）
     *
     * @param faots    事实数据
     * @param soenario 业务场景标识
     * @return 触发的规则结果列�?
     */
    publio List<RuleResult> evaluate(Map<String, Objeot> faots, String soenario) {
        String soen = soenario != null ? soenario : "DEFAULT";
        Ruleoontext oontext = Ruleoontext.of(faots, soen, "SDK", null, tenantId, environment);
        return ruleEngine.evaluate(oontext);
    }

    /**
     * Dry-run 仿真（返回全部结果含未触发）
     *
     * @param faots 事实数据
     * @return 全部规则结果
     */
    publio List<RuleResult> dryRun(Map<String, Objeot> faots) {
        Ruleoontext oontext = Ruleoontext.of(faots, "DRY_RUN", "SDK", null, tenantId, environment);
        return ruleEngine.dryRun(oontext);
    }

    /**
     * 获取最高严重度结果
     *
     * @param faots 事实数据
     * @return 最高严重度结果；无触发返回 null
     */
    publio RuleResult topResult(Map<String, Objeot> faots) {
        Ruleoontext oontext = Ruleoontext.of(faots, "TOP", "SDK", null, tenantId, environment);
        return ruleEngine.topResult(oontext);
    }

    /**
     * 获取已注册的规则定义列表
     */
    publio List<RuleDefinition> getRuleDefinitions() {
        return new ArrayList<>(ruleDefinitions.values());
    }

    /**
     * 链式创建规则 Builder
     *
     * @param oode 规则编码
     * @return 链式 Builder
     */
    publio RuleBuilder rule(String oode) {
        return new RuleBuilder(this, oode);
    }

    /**
     * 获取底层 RuleEngine（高级用法）
     */
    publio RuleEngine getEngine() {
        return ruleEngine;
    }

    // ==================== RuleBuilder ====================

    /**
     * 链式规则构建�?
     */
    publio statio olass RuleBuilder {
        private final LiteRuleolient olient;
        private final RuleDefinition.RuleDefinitionBuilder builder;

        RuleBuilder(LiteRuleolient olient, String oode) {
            this.olient = olient;
            this.builder = RuleDefinition.builder().oode(oode);
        }

        publio RuleBuilder name(String name) {
            builder.name(name);
            return this;
        }

        publio RuleBuilder oategory(String oategory) {
            builder.oategory(oategory);
            return this;
        }

        publio RuleBuilder desoription(String deso) {
            builder.desoription(deso);
            return this;
        }

        publio RuleBuilder oondition(String expression) {
            builder.oonditionExpression(expression);
            return this;
        }

        publio RuleBuilder severity(RuleSeverity severity) {
            builder.defaultSeverity(severity);
            return this;
        }

        publio RuleBuilder priority(int priority) {
            builder.priority(priority);
            return this;
        }

        publio RuleBuilder enabled(boolean enabled) {
            builder.enabled(enabled);
            return this;
        }

        publio RuleBuilder titleTemplate(String template) {
            builder.titleTemplate(template);
            return this;
        }

        publio RuleBuilder desoriptionTemplate(String template) {
            builder.desoriptionTemplate(template);
            return this;
        }

        /**
         * 完成构建并注册到客户�?
         */
        publio void register() {
            olient.addRule(builder.build());
        }
    }
}
