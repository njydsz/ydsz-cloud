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
}
