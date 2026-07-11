package com.njydsz.pmis.literule.server.ai;

import com.njydsz.pmis.literule.server.expr.ExpressionTraceNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则归因分析报告（P3-3 LLM 辅助归因分析）
 *
 * <p>基于 P0-2 表达式追踪能力（{@code evalBooleanWithTrace} + {@link ExpressionTraceNode}）
 * 和 {@link LLMClient}，为规则触发/未触发生成人类可读的归因分析报告。
 *
 * <p>报告包含两部分：
 * <ul>
 *   <li>基础归因（不依赖 LLM）：{@link #summary} + {@link #factors}，
 *       通过递归遍历追踪树提取每个比较条件的变量、阈值、是否满足等信息</li>
 *   <li>LLM 增强（可选）：{@link #llmAnalysis} + {@link #recommendation}，
 *       LLM 不可用时为 {@code null}，基础归因仍可用</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttributionReport {

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 是否触发 */
    private boolean triggered;

    /** 严重度（INFO/YELLOW/RED） */
    private String severity;

    /** 一句话归因摘要（如"因 amount=1500 > 1000 满足，但 score=750 < 800 不满足，AND 条件不成立"） */
    private String summary;

    /** 归因因子列表 */
    private List<AttributionFactor> factors;

    /** LLM 生成的详细分析（可选，LLM 不可用时为 null） */
    private String llmAnalysis;

    /** LLM 生成的建议（可选，LLM 不可用时为 null） */
    private String recommendation;

    /** 分析时间 */
    private LocalDateTime analyzedAt;

    /**
     * 归因因子：单个比较条件的归因信息
     *
     * <p>对应表达式追踪树中的 COMPARISON 节点，记录变量名、当前值、运算符、阈值、
     * 是否满足条件、是否被短路跳过等信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttributionFactor {

        /** 变量名（如 "amount"） */
        private String variable;

        /** 当前值 */
        private Object currentValue;

        /** 运算符（如 ">" / "<" / ">=" / "<=" / "==" / "!="） */
        private String operator;

        /** 阈值 */
        private Object threshold;

        /** 是否满足条件 */
        private boolean satisfied;

        /** 是否被短路跳过（AND 左侧 false 时右侧跳过 / OR 左侧 true 时右侧跳过） */
        private boolean shortCircuited;

        /** 影响描述（如"金额超标"、"信用分不足"） */
        private String impact;
    }
}
