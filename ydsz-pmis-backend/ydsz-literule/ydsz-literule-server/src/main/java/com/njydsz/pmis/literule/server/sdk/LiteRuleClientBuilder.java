package com.njydsz.literule.server.sdk;

import com.njydsz.literule.api.RuleEngine;
import com.njydsz.literule.server.core.DefaultRuleEngine;
import com.njydsz.literule.server.expr.ExpressionEvaluator;
import com.njydsz.literule.server.expr.liteexpr.LiteExprEvaluator;

/**
 * LiteRuleClient 构建器
 *
 * <p>链式构建 {@link LiteRuleClient}，支持嵌入式（无 Spring）和 Spring 集成两种模式。
 *
 * <h3>嵌入式快速构建</h3>
 * <pre>{@code
 * LiteRuleClient client = LiteRuleClient.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 * }</pre>
 *
 * <h3>自定义引擎</h3>
 * <pre>{@code
 * LiteRuleClient client = LiteRuleClient.builder()
 *     .ruleEngine(myEngine)
 *     .evaluator(myEvaluator)
 *     .build();
 * }</pre>
 *
 * @since 2.0.0
 */
public class LiteRuleClientBuilder {

    private RuleEngine ruleEngine;
    private ExpressionEvaluator evaluator;
    private String tenantId = "1";
    private String environment = "default";

    /**
     * 设置自定义规则引擎
     */
    public LiteRuleClientBuilder ruleEngine(RuleEngine engine) {
        this.ruleEngine = engine;
        return this;
    }

    /**
     * 设置自定义表达式求值器
     */
    public LiteRuleClientBuilder evaluator(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
        return this;
    }

    /**
     * 设置租户 ID
     */
    public LiteRuleClientBuilder tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * 设置环境标识
     */
    public LiteRuleClientBuilder environment(String environment) {
        this.environment = environment;
        return this;
    }

    /**
     * 构建 LiteRuleClient
     *
     * <p>如果未提供 RuleEngine，则自动创建 {@link DefaultRuleEngine}；
     * 如果未提供 ExpressionEvaluator，则自动创建 {@link LiteExprEvaluator}。
     */
    public LiteRuleClient build() {
        if (evaluator == null) {
            evaluator = new LiteExprEvaluator();
        }
        if (ruleEngine == null) {
            ruleEngine = new DefaultRuleEngine();
        }
        return new LiteRuleClient(ruleEngine, evaluator, tenantId, environment);
    }
}
