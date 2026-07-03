package com.njydsz.pmis.literule.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 规则定义（元数据）
 *
 * <p>描述一条可配置规则的完整元信息，支持从数据库加载或编程式创建。
 * conditionExpression 为 Aviator 表达式，返回 boolean；actionExpression 可选，用于动态生成结果描述。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则编码（唯一） */
    private String code;

    /** 规则名称 */
    private String name;

    /** 规则类别 */
    private String category;

    /** 规则描述 */
    private String description;

    /**
     * 条件表达式（Aviator 语法）
     * <p>示例：{@code evmRedCount >= 3} 或 {@code grossMargin < 0.05 && confirmedRevenue > 0}
     */
    private String conditionExpression;

    /**
     * 严重度表达式（Aviator 语法，可选）
     * <p>当条件满足时，根据上下文动态决定严重度。
     * 示例：{@code benchIdleCost >= 1000000 ? 'RED' : 'YELLOW'}
     * 为空时使用 {@link #defaultSeverity}
     */
    private String severityExpression;

    /** 默认严重度（当 severityExpression 为空时使用） */
    private RuleSeverity defaultSeverity;

    /** 标题模板（支持 ${var} 占位符） */
    private String titleTemplate;

    /** 描述模板（支持 ${var} 占位符） */
    private String descriptionTemplate;

    /** 优先级（数值越小越先执行） */
    @Builder.Default
    private int priority = Rule.DEFAULT_PRIORITY;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /** 影响范围 */
    private String scope;

    /** 是否可下钻 */
    @Builder.Default
    private boolean drilldownAvailable = true;

    /** 当前版本号 */
    @Builder.Default
    private int version = 1;

    /**
     * 租户 ID
     *
     * <p>多租户隔离标识，单租户部署下默认为 1。
     * 字段已预留，运行时按租户过滤的能力待 v2.0 多租户化阶段启用。
     *
     * @since 1.4.0
     */
    @Builder.Default
    private long tenantId = 1L;

    /** 生命周期状态 */
    @Builder.Default
    private String status = "PUBLISHED";

    /** 生效时间 */
    private String effectiveFrom;

    /** 失效时间 */
    private String effectiveTo;

    /** 审核人 */
    private String reviewedBy;

    /** 审核时间 */
    private String reviewedAt;

    /** 审核意见 */
    private String reviewComment;

    /**
     * 灰度比例（0.0~1.0，0 表示不启用灰度）
     *
     * <p>当 canaryRatio > 0 且存在候选版本（canaryDefinition 非空）时，
     * 引擎按此比例将流量分到候选版本。
     *
     * @since 1.4.0
     */
    @Builder.Default
    private double canaryRatio = 0.0;

    /**
     * 灰度条件（Aviator 表达式列表，AND 关系）
     *
     * <p>仅当 canaryRatio > 0 时生效；满足全部条件才进入灰度流量分桶。
     * 示例：{@code ["tenantId == 'T001'", "userRole == 'ADMIN'"]}
     * 为空时仅按 canaryRatio 比例分桶。
     *
     * @since 1.4.0
     */
    private java.util.List<String> canaryConditions;

    /**
     * 灰度候选版本表达式（条件/严重度表达式，覆盖主版本）
     *
     * <p>当流量被分到灰度桶时，使用此候选表达式构造一条临时规则进行评估，
     * 结果会被标记 {@link RuleResult#isCanary()} = true，便于运营对比新旧命中差异。
     *
     * @since 1.4.0
     */
    private String canaryConditionExpression;

    /** 灰度候选版本的严重度表达式 */
    private String canarySeverityExpression;
}
