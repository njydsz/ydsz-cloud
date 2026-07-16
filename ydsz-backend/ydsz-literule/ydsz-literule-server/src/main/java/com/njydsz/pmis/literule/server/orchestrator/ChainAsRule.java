package com.njydsz.literule.server.orchestrator;

import java.util.List;

import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;
import com.njydsz.literule.server.expr.ExpressionEvaluator;

/**
 * RuleChain → Rule 适配器（P1-7）
 *
 * <p>将 {@link RuleChain} 包装为 {@link Rule} 接口实例，
 * 使嵌套子链可作为规则节点被父链引用。
 *
 * <p>评估时委托给内部的 {@link RuleChain#evaluate(RuleContext, ExpressionEvaluator)}，
 * 返回全部已触发结果中的第一条（作为 RuleResult）。
 * 若有多个触发结果，其余通过 {@link RuleResult#getCollectedResults()} 收集。
 *
 * @since 1.6.0
 */
public class ChainAsRule implements Rule {

    private final RuleChain chain;

    public ChainAsRule(RuleChain chain) {
        this.chain = chain;
    }

    @Override
    public String getCode() {
        return "CHAIN_" + chain.getChainType().name();
    }

    @Override
    public String getName() {
        return chain.getChainType().getDesc() + " 子链";
    }

    @Override
    public String getCategory() {
        return "CHAIN";
    }

    @Override
    public int getPriority() {
        return Rule.DEFAULT_PRIORITY;
    }

    @Override
    public RuleResult evaluate(RuleContext context) {
        // 嵌套子链评估时不需要 ExpressionEvaluator（THEN/WHEN/FOR/WHILE 不需要）
        // 仅 IF/ELIF/SWITCH 需要 evaluator，此处使用 null 降级
        List<RuleResult> results = chain.evaluate(context, null);
        if (results == null || results.isEmpty()) {
            return RuleResult.builder()
                    .ruleCode(getCode())
                    .ruleName(getName())
                    .triggered(false)
                    .build();
        }
        RuleResult main = results.get(0);
        if (results.size() > 1) {
            main.setCollectedResults(results.subList(1, results.size()));
        }
        return main;
    }

    /**
     * 获取包装的 RuleChain
     *
     * @return 原始 RuleChain
     */
    public RuleChain getChain() {
        return chain;
    }
}
