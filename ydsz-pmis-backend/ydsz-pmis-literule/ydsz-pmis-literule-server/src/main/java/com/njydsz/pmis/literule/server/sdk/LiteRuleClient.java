package com.njydsz.pmis.literule.server.sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.server.impl.ExpressionRule;

/**
 * LiteRule SDK 入口 —— 面向 Java 开发者的极简 API
 *
 * <p>提供链式 Builder 构建规则、一行代码评估的极简体验。
 * 适用于嵌入式场景（不依赖 Spring）和 Spring Boot 场景（通过 AutoConfiguration 自动注入）。
 *
 * <h3>快速入门（嵌入式）</h3>
 * <pre>{@code
 * LiteRuleClient client = LiteRuleClient.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 *
 * // 编程式注册规则
 * client.addRule(RuleDefinition.builder()
 *     .code("R001")
 *     .name("高额预警")
 *     .conditionExpression("amount > 10000")
 *     .defaultSeverity(RuleSeverity.RED)
 *     .build());
 *
 * // 评估
 * List<RuleResult> results = client.evaluate(Map.of("amount", 15000));
 * }</pre>
 *
 * <h3>链式 Builder 注册规则</h3>
 * <pre>{@code
 * client.rule("R002")
 *     .name("低利润告警")
 *     .condition("grossMargin < 0.05 && confirmedRevenue > 0")
 *     .severity(RuleSeverity.YELLOW)
 *     .priority(10)
 *     .register();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public class LiteRuleClient {

    private final RuleEngine ruleEngine;
    private final ExpressionEvaluator evaluator;
    private final String tenantId;
    private final String environment;
    private final Map<String, RuleDefinition> ruleDefinitions = new ConcurrentHashMap<>();

    LiteRuleClient(RuleEngine ruleEngine, ExpressionEvaluator evaluator,
                   String tenantId, String environment) {
        this.ruleEngine = Objects.requireNonNull(ruleEngine, "ruleEngine");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.tenantId = tenantId != null ? tenantId : "1";
        this.environment = environment != null ? environment : "default";
    }

    /**
     * 创建 Builder
     */
    public static LiteRuleClientBuilder builder() {
        return new LiteRuleClientBuilder();
    }

    /**
     * 编程式注册规则定义
     *
     * @param definition 规则定义
     */
    public void addRule(RuleDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(definition.getCode(), "rule code");

        // 填充租户和环境（如果未设置）
        if (definition.getTenantId() == null || definition.getTenantId().equals("1")) {
            definition.setTenantId(tenantId);
        }
        if (definition.getEnvironment() == null || definition.getEnvironment().equals("default")) {
            definition.setEnvironment(environment);
        }

        ExpressionRule rule = new ExpressionRule(definition, evaluator);
        ruleEngine.register(rule);
        ruleDefinitions.put(definition.getCode(), definition);
    }

    /**
     * 移除规则
     *
     * @param ruleCode 规则编码
     */
    public void removeRule(String ruleCode) {
        ruleEngine.unregister(ruleCode);
        ruleDefinitions.remove(ruleCode);
    }

    /**
     * 评估规则（使用默认租户和环境）
     *
     * @param facts 事实数据
     * @return 触发的规则结果列表
     */
    public List<RuleResult> evaluate(Map<String, Object> facts) {
        return evaluate(facts, null);
    }

    /**
     * 获取已注册的规则数量
     */
    public int ruleCount() {
        return ruleDefinitions.size();
    }

    /**
     * 评估规则（指定场景）
     *
     * @param facts    事实数据
     * @param scenario 业务场景标识
     * @return 触发的规则结果列表
     */
    public List<RuleResult> evaluate(Map<String, Object> facts, String scenario) {
        String scen = scenario != null ? scenario : "DEFAULT";
        RuleContext context = RuleContext.of(facts, scen, "SDK", null, tenantId, environment);
        return ruleEngine.evaluate(context);
    }

    /**
     * Dry-run 仿真（返回全部结果含未触发）
     *
     * @param facts 事实数据
     * @return 全部规则结果
     */
    public List<RuleResult> dryRun(Map<String, Object> facts) {
        RuleContext context = RuleContext.of(facts, "DRY_RUN", "SDK", null, tenantId, environment);
        return ruleEngine.dryRun(context);
    }

    /**
     * 获取最高严重度结果
     *
     * @param facts 事实数据
     * @return 最高严重度结果；无触发返回 null
     */
    public RuleResult topResult(Map<String, Object> facts) {
        RuleContext context = RuleContext.of(facts, "TOP", "SDK", null, tenantId, environment);
        return ruleEngine.topResult(context);
    }

    /**
     * 获取已注册的规则定义列表
     */
    public List<RuleDefinition> getRuleDefinitions() {
        return new ArrayList<>(ruleDefinitions.values());
    }

    /**
     * 链式创建规则 Builder
     *
     * @param code 规则编码
     * @return 链式 Builder
     */
    public RuleBuilder rule(String code) {
        return new RuleBuilder(this, code);
    }

    /**
     * 获取底层 RuleEngine（高级用法）
     */
    public RuleEngine getEngine() {
        return ruleEngine;
    }

    // ==================== RuleBuilder ====================

    /**
     * 链式规则构建器
     */
    public static class RuleBuilder {
        private final LiteRuleClient client;
        private final RuleDefinition.RuleDefinitionBuilder builder;

        RuleBuilder(LiteRuleClient client, String code) {
            this.client = client;
            this.builder = RuleDefinition.builder().code(code);
        }

        public RuleBuilder name(String name) {
            builder.name(name);
            return this;
        }

        public RuleBuilder category(String category) {
            builder.category(category);
            return this;
        }

        public RuleBuilder description(String desc) {
            builder.description(desc);
            return this;
        }

        public RuleBuilder condition(String expression) {
            builder.conditionExpression(expression);
            return this;
        }

        public RuleBuilder severity(RuleSeverity severity) {
            builder.defaultSeverity(severity);
            return this;
        }

        public RuleBuilder priority(int priority) {
            builder.priority(priority);
            return this;
        }

        public RuleBuilder enabled(boolean enabled) {
            builder.enabled(enabled);
            return this;
        }

        public RuleBuilder titleTemplate(String template) {
            builder.titleTemplate(template);
            return this;
        }

        public RuleBuilder descriptionTemplate(String template) {
            builder.descriptionTemplate(template);
            return this;
        }

        /**
         * 完成构建并注册到客户端
         */
        public void register() {
            client.addRule(builder.build());
        }
    }
}
