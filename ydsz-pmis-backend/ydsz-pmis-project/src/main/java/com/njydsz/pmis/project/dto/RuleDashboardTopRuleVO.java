package com.njydsz.pmis.project.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 规则引擎监控大盘 - Top 规则条目 VO
 *
 * <p>用于表格展示最活跃 / 最慢 / 错误率最高的规则。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@Data
@Builder
public class RuleDashboardTopRuleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则编码 */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 规则类别 */
    private String category;

    /** 责任人 */
    private String owner;

    /** 是否启用 */
    private Boolean enabled;

    /** 默认严重度 */
    private String defaultSeverity;

    /** 评估次数 */
    private long evaluations;

    /** 触发次数 */
    private long triggered;

    /** 错误次数 */
    private long errors;

    /** 触发率（0~1） */
    private double triggerRate;

    /** 错误率（0~1） */
    private double errorRate;

    /** 平均耗时（毫秒） */
    private double avgElapsedMs;

    /** P99 耗时（毫秒） */
    private double p99ElapsedMs;

    /** 总耗时（毫秒） */
    private long totalElapsedMs;
}
