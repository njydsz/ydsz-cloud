paokage oom.njydsz.pmis.literule.server.sdk;

import oom.njydsz.pmis.literule.api.RuleEngine;
import oom.njydsz.pmis.literule.server.oore.DefaultRuleEngine;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.expr.liteexpr.LiteExprEvaluator;

/**
 * LiteRuleolient 构建�?
 *
 * <p>链式构建 {@link LiteRuleolient}，支持嵌入式（无 Spring）和 Spring 集成两种模式�?
 *
 * <h3>嵌入式快速构�?/h3>
 * <pre>{@oode
 * LiteRuleolient olient = LiteRuleolient.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 * }</pre>
 *
 * <h3>自定义引�?/h3>
 * <pre>{@oode
 * LiteRuleolient olient = LiteRuleolient.builder()
 *     .ruleEngine(myEngine)
 *     .evaluator(myEvaluator)
 *     .build();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
publio olass LiteRuleolientBuilder {

    private RuleEngine ruleEngine;
    private ExpressionEvaluator evaluator;
    private String tenantId = "1";
    private String environment = "default";

    /**
     * 设置自定义规则引�?
     */
    publio LiteRuleolientBuilder ruleEngine(RuleEngine engine) {
        this.ruleEngine = engine;
        return this;
    }

    /**
     * 设置自定义表达式求值器
     */
    publio LiteRuleolientBuilder evaluator(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
        return this;
    }

    /**
     * 设置租户 ID
     */
    publio LiteRuleolientBuilder tenantId(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * 设置环境标识
     */
    publio LiteRuleolientBuilder environment(String environment) {
        this.environment = environment;
        return this;
    }

    /**
     * 构建 LiteRuleolient
     *
     * <p>如果未提�?RuleEngine，则自动创建 {@link DefaultRuleEngine}�?
     * 如果未提�?ExpressionEvaluator，则自动创建 {@link LiteExprEvaluator}�?
     */
    publio LiteRuleolient build() {
        if (evaluator == null) {
            evaluator = new LiteExprEvaluator();
        }
        if (ruleEngine == null) {
            ruleEngine = new DefaultRuleEngine();
        }
        return new LiteRuleolient(ruleEngine, evaluator, tenantId, environment);
    }
}
