package com.njydsz.pmis.literule.adaptive;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 阈值分析结果（P3-4 自适应智能风控）
 *
 * <p>对一条规则的单一阈值比较项（如 {@code amount > 1000}）的调整建议。
 * 一条规则的复杂条件表达式可能被拆分为多个 {@link ThresholdAnalysis}（如 AND/OR 组合表达式）。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@link #currentThreshold} - 当前表达式中提取的阈值</li>
 *   <li>{@link #suggestedThreshold} - 基于历史数据计算出的建议阈值</li>
 *   <li>{@link #confidence} - 建议置信度（0~1），样本量越大、分布越集中越高</li>
 *   <li>{@link #reason} - 调整原因（LLM 生成或模板生成）</li>
 *   <li>{@link #strategy} - 采用的调整策略</li>
 *   <li>{@link #distribution} - 数据分布统计</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdAnalysis implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String ruleCode;

    /** 变量名（如 "amount"） */
    @NotBlank(message = "变量名不能为空")
    private String variable;

    /** 运算符（如 "&gt;"、"&gt;="、"&lt;"、"&lt;="、"=="、"!="） */
    @NotBlank(message = "运算符不能为空")
    private String operator;

    /** 当前阈值 */
    private double currentThreshold;

    /** 建议阈值 */
    private double suggestedThreshold;

    /** 置信度（0~1） */
    private double confidence;

    /** 调整原因（自然语言描述） */
    private String reason;

    /** 调整策略 */
    private ThresholdStrategy strategy;

    /** 数据分布统计 */
    private DistributionStats distribution;

    /** 是否已应用（应用后置为 true，避免重复应用） */
    @Builder.Default
    private boolean applied = false;

    /** 建议生成时间（ISO-8601 字符串） */
    private String suggestedAt;
}
