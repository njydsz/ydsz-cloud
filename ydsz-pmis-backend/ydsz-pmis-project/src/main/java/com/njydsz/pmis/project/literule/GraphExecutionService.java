package com.njydsz.pmis.project.literule;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.orchestrator.ChainGraphConverter;
import com.njydsz.pmis.literule.orchestrator.RuleChain;
import com.njydsz.pmis.literule.orchestrator.RuleChainGraph;
import com.njydsz.pmis.literule.orchestrator.RuleGraphValidator;
import com.njydsz.pmis.literule.spi.GraphExecutionProvider;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 画布执行服务（P0-1 执行闭环）
 *
 * <p>将可视化画布 {@link RuleChainGraph} 通过 {@link ChainGraphConverter#toChain}
 * 转换为可执行的 {@link RuleChain}，直接调用 {@link RuleChain#evaluate} 执行评估，
 * 补齐"画布可保存但不可执行"的闭环缺口。
 *
 * <p>核心能力：
 * <ul>
 *   <li>{@link #dryRunGraph} - 对画布执行 Dry-run 仿真（不记录统计、不发布事件）</li>
 *   <li>{@link #evaluateGraph} - 对画布执行真实评估（记录统计）</li>
 *   <li>{@link #validateAndBuildChain} - 校验画布并构建可执行规则链（供外部复用）</li>
 * </ul>
 *
 * <p>规则实例解析：通过 {@link RuleConfigProvider} 按 ruleCode 加载 {@link RuleDefinition}，
 * 构造 {@link ExpressionRule} 作为 {@link ChainGraphConverter.RuleResolver} 的返回值。
 * 画布中引用的规则若不存在或已禁用，对应节点将被跳过（不阻断整链执行）。
 *
 * <p>实现 {@link GraphExecutionProvider} SPI，供 literule 模块的 Controller 反转依赖调用。
 *
 * @author ydsz-pmis-team
 * @since 1.5.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphExecutionService implements GraphExecutionProvider {

    private final RuleChainGraphService ruleChainGraphService;
    private final RuleConfigProvider ruleConfigProvider;
    private final ExpressionEvaluator expressionEvaluator;

    /**
     * 对指定规则的画布执行 Dry-run 仿真
     *
     * <p>不记录统计、不发布事件，仅返回评估结果。画布不存在时抛出
     * {@link IllegalArgumentException}。
     *
     * @param ruleCode 规则编码（画布关联 key）
     * @param facts    事实数据
     * @return 评估结果列表（已触发的规则结果）；画布为空或转换失败返回空列表
     */
    public List<RuleResult> dryRunGraph(String ruleCode, Map<String, Object> facts) {
        RuleChain chain = validateAndBuildChain(ruleCode);
        if (chain == null) {
            return Collections.emptyList();
        }
        RuleContext context = RuleContext.of(facts, null, "GRAPH_DRYRUN", null);
        // Dry-run 不走 statsRecorder，传 null
        return chain.evaluate(context, expressionEvaluator, null);
    }

    /**
     * 对指定规则的画布执行真实评估（记录统计到引擎）
     *
     * <p>与 Dry-run 的区别：评估结果会通过传入的 statsRecorder
     * 记录到引擎统计中，用于监控大盘。
     *
     * @param ruleCode    规则编码
     * @param facts       事实数据
     * @param scenario    场景（用于统计维度）
     * @param statsRecorder 统计记录器（可为 null，为 null 时不记录统计）
     * @return 评估结果列表
     */
    public List<RuleResult> evaluateGraph(String ruleCode, Map<String, Object> facts,
                                           String scenario,
                                           com.njydsz.pmis.literule.api.StatsRecorder statsRecorder) {
        RuleChain chain = validateAndBuildChain(ruleCode);
        if (chain == null) {
            return Collections.emptyList();
        }
        RuleContext context = RuleContext.of(facts, scenario, "GRAPH_EVAL", null);
        return chain.evaluate(context, expressionEvaluator, statsRecorder);
    }

    /**
     * 校验画布并构建可执行规则链
     *
     * <p>步骤：
     * <ol>
     *   <li>从 {@link RuleChainGraphService} 加载画布</li>
     *   <li>用 {@link RuleGraphValidator} 校验结构，存在 ERROR 则抛出</li>
     *   <li>用 {@link ChainGraphConverter#toChain} 转换为 {@link RuleChain}</li>
     * </ol>
     *
     * @param ruleCode 规则编码
     * @return 可执行规则链；画布不存在或无有效节点返回 null
     * @throws IllegalArgumentException 画布存在但校验失败
     */
    public RuleChain validateAndBuildChain(String ruleCode) {
        RuleChainGraph graph = ruleChainGraphService.getByRuleCode(ruleCode);
        if (graph == null) {
            log.warn("[GraphExec] 画布不存在: ruleCode={}", ruleCode);
            return null;
        }
        List<RuleGraphValidator.GraphValidationIssue> issues = RuleGraphValidator.validate(graph);
        if (!RuleGraphValidator.isValid(issues)) {
            List<String> errors = issues.stream()
                    .filter(i -> i.getLevel() == RuleGraphValidator.Level.ERROR)
                    .map(i -> "[" + i.getCode() + "] " + i.getMessage())
                    .toList();
            throw new IllegalArgumentException("画布校验失败: " + String.join("; ", errors));
        }
        RuleChain chain = ChainGraphConverter.toChain(graph, this::resolveRule);
        if (chain == null) {
            log.info("[GraphExec] 画布转换为空链（无有效节点）: ruleCode={}", ruleCode);
        }
        return chain;
    }

    /**
     * 规则解析器：按 ruleCode 从配置提供者加载并构建 ExpressionRule
     *
     * <p>解析失败（规则不存在/已禁用）时返回 null，由 {@link ChainGraphConverter}
     * 自动跳过对应节点。
     *
     * @param ruleCode 规则编码
     * @return 规则实例；不存在或已禁用返回 null
     */
    private Rule resolveRule(String ruleCode) {
        RuleDefinition def = ruleConfigProvider.findByCode(ruleCode);
        if (def == null) {
            log.warn("[GraphExec] 画布引用的规则不存在: ruleCode={}", ruleCode);
            return null;
        }
        if (!def.isEnabled()) {
            log.debug("[GraphExec] 画布引用的规则已禁用，跳过: ruleCode={}", ruleCode);
            return null;
        }
        return new ExpressionRule(def, expressionEvaluator);
    }

    /**
     * 收集画布中引用了但已失效（不存在/已禁用）的规则编码
     *
     * <p>用于前端提示用户"画布中存在失效节点"，避免静默跳过导致评估结果与预期不符。
     *
     * @param ruleCode 规则编码
     * @return 失效规则编码列表（无失效返回空列表）
     */
    public List<String> collectInvalidReferences(String ruleCode) {
        RuleChainGraph graph = ruleChainGraphService.getByRuleCode(ruleCode);
        if (graph == null || graph.getNodes() == null) {
            return Collections.emptyList();
        }
        List<String> invalid = new ArrayList<>();
        for (var node : graph.getNodes()) {
            if ("SINGLE".equals(node.getNodeType()) && node.getRuleCode() != null) {
                RuleDefinition def = ruleConfigProvider.findByCode(node.getRuleCode());
                if (def == null || !def.isEnabled()) {
                    invalid.add(node.getRuleCode());
                }
            }
        }
        return invalid;
    }
}
