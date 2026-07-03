package com.njydsz.pmis.literule.orchestrator;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.StatsRecorder;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 规则编排器，管理多条 {@link RuleChain}
 *
 * <p>编排器是规则编排能力的顶层入口，维护一组规则链并按注册顺序依次执行，
 * 合并全部链的评估结果。规则链之间相互独立，单链异常不影响其他链。
 *
 * <p>支持将编排层的规则执行统计统一记录到引擎统计中，消除编排层与引擎层统计割裂。
 *
 * <p>典型用法：
 * <pre>
 *   RuleOrchestrator orchestrator = new RuleOrchestrator(new AviatorExpressionEvaluator());
 *   orchestrator.setStatsRecorder(engine.asStatsRecorder());
 *   orchestrator.register(RuleChain.then(r1, r2));
 *   orchestrator.register(RuleChain.ifThen("amount &gt; 1000", alertRule));
 *   orchestrator.register(RuleChain.switchOn("type", branches));
 *
 *   List&lt;RuleResult&gt; results = orchestrator.evaluate(context);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
public class RuleOrchestrator {

    /** 已注册的规则链列表（按注册顺序） */
    private final CopyOnWriteArrayList<RuleChain> chains = new CopyOnWriteArrayList<>();

    /** 表达式求值器（IF/SWITCH 链需要） */
    private final ExpressionEvaluator evaluator;

    /** 统计记录器（可选，设置后编排层执行统计将统一记录到引擎） */
    private volatile StatsRecorder statsRecorder;

    /**
     * 构造编排器
     *
     * @param evaluator 表达式求值器（IF/SWITCH 语义需要）
     */
    public RuleOrchestrator(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * 设置统计记录器
     *
     * <p>设置后，编排层中每个规则评估的统计数据将统一记录到引擎统计中，
     * 消除编排层与引擎层统计割裂问题。
     *
     * @param statsRecorder 统计记录器（可为 null，表示不记录统计）
     * @since 1.3.0
     */
    public void setStatsRecorder(StatsRecorder statsRecorder) {
        this.statsRecorder = statsRecorder;
    }

    /**
     * 注册一条规则链
     *
     * @param chain 规则链（不能为 null）
     */
    public void register(RuleChain chain) {
        Objects.requireNonNull(chain, "chain 不能为 null");
        chains.add(chain);
        log.info("[LiteRule-Orchestrator] 规则链已注册: type={}, size={}",
                chain.getChainType(), chains.size());
    }

    /**
     * 评估全部已注册规则链，合并结果
     *
     * <p>按注册顺序依次执行每条链，合并全部已触发的结果。
     * 单链异常将被隔离（记录日志并跳过），不影响其他链。
     * 若已设置 {@link StatsRecorder}，编排层的规则执行统计将统一记录。
     *
     * @param context 规则上下文
     * @return 全部链合并后的已触发结果列表；无触发返回空列表
     */
    public List<RuleResult> evaluate(RuleContext context) {
        return evaluate(context, statsRecorder);
    }

    /**
     * 评估全部已注册规则链，合并结果（指定统计记录器）
     *
     * @param context       规则上下文
     * @param statsRecorder 统计记录器（可为 null）
     * @return 全部链合并后的已触发结果列表
     * @since 1.3.0
     */
    public List<RuleResult> evaluate(RuleContext context, StatsRecorder statsRecorder) {
        Objects.requireNonNull(context, "context 不能为 null");
        List<RuleResult> all = new ArrayList<>();
        for (RuleChain chain : chains) {
            try {
                List<RuleResult> chainResults = chain.evaluate(context, evaluator, statsRecorder);
                if (chainResults != null && !chainResults.isEmpty()) {
                    all.addAll(chainResults);
                }
            } catch (Exception e) {
                log.warn("[LiteRule-Orchestrator] 规则链评估异常: type={}, error={}",
                        chain.getChainType(), e.getMessage());
            }
        }
        return all;
    }

    /**
     * 获取全部已注册规则链（只读）
     *
     * @return 不可修改的规则链列表
     */
    public List<RuleChain> getChains() {
        return List.copyOf(chains);
    }

    /**
     * 获取表达式求值器
     *
     * @return 表达式求值器
     */
    public ExpressionEvaluator getEvaluator() {
        return evaluator;
    }
}
