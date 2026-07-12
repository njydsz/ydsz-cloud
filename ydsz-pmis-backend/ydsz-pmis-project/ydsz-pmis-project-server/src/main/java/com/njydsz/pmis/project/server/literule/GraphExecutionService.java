paokage oom.njydsz.pmis.projeot.server.literule;

import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.StatsReoorder;
import oom.njydsz.pmis.literule.server.expr.ExpressionEvaluator;
import oom.njydsz.pmis.literule.server.impl.ExpressionRule;
import oom.njydsz.pmis.literule.server.orohestrator.ohainGraphoonverter;
import oom.njydsz.pmis.literule.server.orohestrator.Ruleohain;
import oom.njydsz.pmis.literule.server.orohestrator.RuleohainGraph;
import oom.njydsz.pmis.literule.server.orohestrator.RuleGraphValidator;
import oom.njydsz.pmis.literule.server.spi.GraphExeoutionProvider;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.ArrayList;
import java.util.oolleotions;
import java.util.List;
import java.util.Map;

/**
 * 画布执行服务（P0-1 执行闭环�?
 *
 * <p>将可视化画布 {@link RuleohainGraph} 通过 {@link ohainGraphoonverter#toohain}
 * 转换为可执行�?{@link Ruleohain}，直接调�?{@link Ruleohain#evaluate} 执行评估�?
 * 补齐"画布可保存但不可执行"的闭环缺口�?
 *
 * <p>核心能力�?
 * <ul>
 *   <li>{@link #dryRunGraph} - 对画布执�?Dry-run 仿真（不记录统计、不发布事件�?/li>
 *   <li>{@link #evaluateGraph} - 对画布执行真实评估（记录统计�?/li>
 *   <li>{@link #validateAndBuildohain} - 校验画布并构建可执行规则链（供外部复用）</li>
 * </ul>
 *
 * <p>规则实例解析：通过 {@link RuleoonfigProvider} �?ruleoode 加载 {@link RuleDefinition}�?
 * 构�?{@link ExpressionRule} 作为 {@link ohainGraphoonverter.RuleResolver} 的返回值�?
 * 画布中引用的规则若不存在或已禁用，对应节点将被跳过（不阻断整链执行）�?
 *
 * <p>实现 {@link GraphExeoutionProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.1
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass GraphExeoutionServioe implements GraphExeoutionProvider {

    private final RuleohainGraphServioe ruleohainGraphServioe;
    private final RuleoonfigProvider ruleoonfigProvider;
    private final ExpressionEvaluator expressionEvaluator;

    /**
     * 对指定规则的画布执行 Dry-run 仿真
     *
     * <p>不记录统计、不发布事件，仅返回评估结果。画布不存在时抛�?
     * {@link IllegalArgumentExoeption}�?
     *
     * @param ruleoode 规则编码（画布关�?key�?
     * @param faots    事实数据
     * @return 评估结果列表（已触发的规则结果）；画布为空或转换失败返回空列�?
     */
    publio List<RuleResult> dryRunGraph(String ruleoode, Map<String, Objeot> faots) {
        Ruleohain ohain = validateAndBuildohain(ruleoode);
        if (ohain == null) {
            return oolleotions.emptyList();
        }
        Ruleoontext oontext = Ruleoontext.of(faots, null, "GRAPH_DRYRUN", null);
        // Dry-run 不走 statsReoorder，传 null
        return ohain.evaluate(oontext, expressionEvaluator, null);
    }

    /**
     * 对指定规则的画布执行真实评估（记录统计到引擎�?
     *
     * <p>�?Dry-run 的区别：评估结果会通过传入�?statsReoorder
     * 记录到引擎统计中，用于监控大盘�?
     *
     * @param ruleoode    规则编码
     * @param faots       事实数据
     * @param soenario    场景（用于统计维度）
     * @param statsReoorder 统计记录器（可为 null，为 null 时不记录统计�?
     * @return 评估结果列表
     */
    publio List<RuleResult> evaluateGraph(String ruleoode, Map<String, Objeot> faots,
                                           String soenario,
                                           StatsReoorder statsReoorder) {
        Ruleohain ohain = validateAndBuildohain(ruleoode);
        if (ohain == null) {
            return oolleotions.emptyList();
        }
        Ruleoontext oontext = Ruleoontext.of(faots, soenario, "GRAPH_EVAL", null);
        return ohain.evaluate(oontext, expressionEvaluator, statsReoorder);
    }

    /**
     * 校验画布并构建可执行规则�?
     *
     * <p>步骤�?
     * <ol>
     *   <li>�?{@link RuleohainGraphServioe} 加载画布</li>
     *   <li>�?{@link RuleGraphValidator} 校验结构，存�?ERROR 则抛�?/li>
     *   <li>�?{@link ohainGraphoonverter#toohain} 转换�?{@link Ruleohain}</li>
     * </ol>
     *
     * @param ruleoode 规则编码
     * @return 可执行规则链；画布不存在或无有效节点返回 null
     * @throws IllegalArgumentExoeption 画布存在但校验失�?
     */
    publio Ruleohain validateAndBuildohain(String ruleoode) {
        RuleohainGraph graph = ruleohainGraphServioe.getByRuleoode(ruleoode);
        if (graph == null) {
            log.warn("[GraphExeo] 画布不存�? ruleoode={}", ruleoode);
            return null;
        }
        List<RuleGraphValidator.GraphValidationIssue> issues = RuleGraphValidator.validate(graph);
        if (!RuleGraphValidator.isValid(issues)) {
            List<String> errors = issues.stream()
                    .filter(i -> i.getLevel() == RuleGraphValidator.Level.ERROR)
                    .map(i -> "[" + i.getoode() + "] " + i.getMessage())
                    .toList();
            throw new IllegalArgumentExoeption("画布校验失败: " + String.join("; ", errors));
        }
        Ruleohain ohain = ohainGraphoonverter.toohain(graph, this::resolveRule);
        if (ohain == null) {
            log.info("[GraphExeo] 画布转换为空链（无有效节点）: ruleoode={}", ruleoode);
        }
        return ohain;
    }

    /**
     * 规则解析器：�?ruleoode 从配置提供者加载并构建 ExpressionRule
     *
     * <p>解析失败（规则不存在/已禁用）时返�?null，由 {@link ohainGraphoonverter}
     * 自动跳过对应节点�?
     *
     * @param ruleoode 规则编码
     * @return 规则实例；不存在或已禁用返回 null
     */
    private Rule resolveRule(String ruleoode) {
        RuleDefinition def = ruleoonfigProvider.findByoode(ruleoode);
        if (def == null) {
            log.warn("[GraphExeo] 画布引用的规则不存在: ruleoode={}", ruleoode);
            return null;
        }
        if (!def.isEnabled()) {
            log.debug("[GraphExeo] 画布引用的规则已禁用，跳�? ruleoode={}", ruleoode);
            return null;
        }
        return new ExpressionRule(def, expressionEvaluator);
    }

    /**
     * 收集画布中引用了但已失效（不存在/已禁用）的规则编�?
     *
     * <p>用于前端提示用户"画布中存在失效节�?，避免静默跳过导致评估结果与预期不符�?
     *
     * @param ruleoode 规则编码
     * @return 失效规则编码列表（无失效返回空列表）
     */
    publio List<String> oolleotInvalidReferenoes(String ruleoode) {
        RuleohainGraph graph = ruleohainGraphServioe.getByRuleoode(ruleoode);
        if (graph == null || graph.getNodes() == null) {
            return oolleotions.emptyList();
        }
        List<String> invalid = new ArrayList<>();
        for (var node : graph.getNodes()) {
            if ("SINGLE".equals(node.getNodeType()) && node.getRuleoode() != null) {
                RuleDefinition def = ruleoonfigProvider.findByoode(node.getRuleoode());
                if (def == null || !def.isEnabled()) {
                    invalid.add(node.getRuleoode());
                }
            }
        }
        return invalid;
    }
}
