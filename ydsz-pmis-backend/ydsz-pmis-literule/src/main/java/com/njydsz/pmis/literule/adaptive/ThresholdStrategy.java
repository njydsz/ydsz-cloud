package com.njydsz.pmis.literule.adaptive;

/**
 * 阈值调整策略（P3-4 自适应智能风控）
 *
 * <p>对标字节巨量引擎"规则 2.0"的自适应阈值能力，根据历史触发数据自动调整规则阈值。
 *
 * <p>策略选择规则（由 {@link AdaptiveThresholdService} 根据数据特征自动决策）：
 * <ul>
 *   <li>{@link #PERCENTILE} - 数据分布稳定时取 P95/P99 作为新阈值</li>
 *   <li>{@link #FALSE_RATE} - 当前触发率过高（&gt;50%）时提高阈值以降低误报</li>
 *   <li>{@link #MISS_RATE} - 当前触发率过低（&lt;5%）时降低阈值以减少漏报</li>
 *   <li>{@link #BALANCED} - 使用 F1-score 最优阈值（综合精度与召回）</li>
 *   <li>{@link #LLM_SUGGESTED} - LLM 建议阈值（需 AI 增强启用）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public enum ThresholdStrategy {

    /** 分位数策略（取 P95/P99 作为阈值） */
    PERCENTILE,

    /** 误报率控制（降低误报率到目标值） */
    FALSE_RATE,

    /** 漏报率控制（降低漏报率到目标值） */
    MISS_RATE,

    /** 平衡策略（F1-score 最优） */
    BALANCED,

    /** LLM 建议策略 */
    LLM_SUGGESTED
}
