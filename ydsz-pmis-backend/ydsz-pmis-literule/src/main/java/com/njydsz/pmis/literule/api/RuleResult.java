package com.njydsz.pmis.literule.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 规则评估结果
 *
 * <p>单条规则评估后产出：是否触发、严重度、标题、描述、当前值、阈值等。
 * 未触发时 {@link #triggered} = false。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * BREAK 信号编码（循环终止专用）
     *
     * <p>统一常量定义，替代原硬编码魔法字符串 "BREAK"，
     * 避免与真实规则编码冲突。
     *
     * @since 1.3.0
     */
    public static final String BREAK_CODE = "__BREAK__";

    /** 结果 ID */
    @Builder.Default
    private String resultId = UUID.randomUUID().toString();

    /** 规则编码 */
    private String ruleCode;

    /** 规则名 */
    private String ruleName;

    /** 规则类别 */
    private String category;

    /** 是否触发 */
    private boolean triggered;

    /** 严重度（未触发时为 null） */
    private RuleSeverity severity;

    /** 标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 当前值 */
    private String currentValue;

    /** 阈值（参考） */
    private String threshold;

    /** 影响范围 */
    private String scope;

    /** 触发时间 */
    private LocalDateTime triggeredAt;

    /** 是否可下钻 */
    private Boolean drilldownAvailable;

    /** 评估耗时（毫秒） */
    private long elapsedMs;

    /**
     * 快速构建未触发结果
     *
     * @param ruleCode 规则编码
     * @return 未触发的 RuleResult
     */
    public static RuleResult notTriggered(String ruleCode) {
        return RuleResult.builder()
                .ruleCode(ruleCode)
                .triggered(false)
                .triggeredAt(LocalDateTime.now())
                .build();
    }

    /**
     * 快速构建已触发结果
     *
     * @param ruleCode   规则编码
     * @param ruleName   规则名
     * @param category   类别
     * @param severity   严重度
     * @param title      标题
     * @param description 描述
     * @return 已触发的 RuleResult
     */
    public static RuleResult triggered(String ruleCode, String ruleName, String category,
                                       RuleSeverity severity, String title, String description) {
        return RuleResult.builder()
                .ruleCode(ruleCode)
                .ruleName(ruleName)
                .category(category)
                .triggered(true)
                .severity(severity)
                .title(title)
                .description(description)
                .triggeredAt(LocalDateTime.now())
                .drilldownAvailable(true)
                .build();
    }
}
