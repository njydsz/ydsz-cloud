package com.njydsz.pmis.literule.api;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则评估结果
 *
 * <p>单条规则评估后产出：是否触发、严重度、标题、描述、当前值、阈值等。
 * 未触发时 {@link #triggered} = false。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
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
     * 是否为灰度候选版本评估结果
     *
     * <p>当规则定义了 canaryRatio 且当前流量命中灰度桶时，
     * 引擎会同时评估主版本与候选版本，候选版本结果此字段为 true。
     *
     * @since 1.4.0
     */
    @Builder.Default
    private boolean canary = false;

    /** 灰度桶标识（用于运营对比） */
    private String canaryBucket;

    /**
     * COLLECT/RULE_ORDER 命中策略下收集的全部匹配行结果
     *
     * <p>仅当决策表采用 {@link HitPolicy#COLLECT} 或 {@link HitPolicy#RULE_ORDER}
     * 策略且命中多行时填充。主结果（{@code severity/title/description}）取首条
     * 匹配行，其余匹配行以独立 {@link RuleResult} 形式存入此列表，保留命中顺序。
     *
     * <p>对于单结果策略（UNIQUE/FIRST/PRIORITY/ANY），此字段为空列表。
     *
     * @since 1.5.0
     */
    @Builder.Default
    private List<RuleResult> collectedResults = Collections.emptyList();

    /**
     * 追加一个收集结果（用于 COLLECT/RULE_ORDER 策略）
     *
     * @param result 单行匹配结果
     */
    public void addCollectedResult(RuleResult result) {
        if (result == null) return;
        if (collectedResults == null || collectedResults == Collections.<RuleResult>emptyList()) {
            collectedResults = new ArrayList<>();
        }
        collectedResults.add(result);
    }

    /**
     * 是否包含多结果集合
     *
     * @return true 表示当前结果由 COLLECT/RULE_ORDER 策略产出，存在多行匹配
     */
    public boolean hasCollectedResults() {
        return collectedResults != null && !collectedResults.isEmpty();
    }

    /**
     * 获取收集结果的不可变视图
     *
     * @return 收集结果列表；无收集时返回空列表
     */
    public List<RuleResult> getCollectedResultsOrEmpty() {
        return collectedResults == null ? Collections.emptyList() : collectedResults;
    }

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
