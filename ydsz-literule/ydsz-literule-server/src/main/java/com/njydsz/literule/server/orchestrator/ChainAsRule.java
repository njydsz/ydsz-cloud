package com.njydsz.literule.server.orchestrator;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.literule.api.Rule;
import com.njydsz.literule.api.RuleContext;
import com.njydsz.literule.api.RuleResult;

/**
 * RuleChain → Rule 适配器（P1-7）
 *
 * <p>将 {@link RuleChain} 包装为 {@link Rule} 接口实例，
 * 使嵌套子链可作为规则节点被父链引用。
 *
 * <p>评估时委托给内部的 {@link RuleChain#evaluate(RuleContext, ExpressionEngine)}，
 * 返回全部已触发结果中的第一条（作为 RuleResult）。
 * 若有多个触发结果，其余通过 {@link RuleResult#getCollectedResults()} 收集。
 *
 * <p>P0-1 修复：增加 null 求值器防御与 NPE 隔离，
 * 避免单个 Rule 的 null evaluator 导致整个 Chain 失败。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
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
        // 嵌套子链评估时不需要 ExpressionEngine（THEN/WHEN/FOR/WHILE 不需要）
        // 仅 IF/ELIF/SWITCH 需要 evaluator，此处使用 null 降级
        List<RuleResult> results;
        try {
            results = chain.evaluate(context, null);
        } catch (NullPointerException e) {
            // P0-1 修复：单个 Rule 内部 evaluator 为 null 导致 NPE 时，
            // 隔离失败，不阻断整个 Chain 执行
            log.warn("[LiteRule] ChainAsRule {} 内部 Rule 求值器为 null，已跳过: {}",
                    getCode(), e.getMessage());
            return notTriggered();
        }
        if (results == null || results.isEmpty()) {
            return notTriggered();
        }
        RuleResult main = results.get(0);
        if (main == null || !main.isTriggered()) {
            return notTriggered();
        }
        if (results.size() > 1) {
            main.setCollectedResults(results.subList(1, results.size()));
        }
        return main;
    }

    /**
     * 构建未触发的结果
     *
     * @return 未触发的 RuleResult
     */
    private RuleResult notTriggered() {
        return RuleResult.builder()
                .ruleCode(getCode())
                .ruleName(getName())
                .triggered(false)
                .build();
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
