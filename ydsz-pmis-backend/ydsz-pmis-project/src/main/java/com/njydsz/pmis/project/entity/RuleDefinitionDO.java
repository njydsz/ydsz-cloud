package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * LiteRule 规则定义 DO
 *
 * <p>映射 pmis_rule_def 表，存储可配置规则的全部元信息。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Data
@TableName("pmis_rule_def")
public class RuleDefinitionDO implements Serializable {

    @Serial
    private static final String serialVersionUID = "1";

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String ruleCode;
    private String ruleName;
    private String category;

    /**
     * 分类路径（P1-9 规则目录树）
     *
     * <p>用 {@code /} 分隔的多级分类，如 {@code "finance/credit/loan"}。
     * 与 category 字段的关系：category 保留作为一级分类标识（兼容老数据/快捷筛选），
     * categoryPath 是规则在目录树中的完整路径（用于左侧树形导航）。
     */
    private String categoryPath;

    /**
     * 责任人（P1-9 规则目录树）
     *
     * <p>规则负责人工号/用户名。Owner 在以下场景使用：
     * <ul>
     *   <li>规则异常告警通知（如执行失败率突增）</li>
     *   <li>AB Test 自动回滚后的通知</li>
     *   <li>规则审核/巡检派单</li>
     * </ul>
     */
    private String owner;

    private String description;
    private String conditionExpression;
    private String severityExpression;
    private String defaultSeverity;
    private String titleTemplate;
    private String descriptionTemplate;
    private Integer priority;
    private Boolean enabled;
    private String scope;

    /**
     * 互斥组名称（同组内首个命中后跳过其余规则；null 表示无互斥组）
     *
     * @since 1.5.0
     */
    private String mutexGroup;

    private Boolean drilldownAvailable;
    private Integer version;

    /** 租户 ID（单租户部署默认 1，多租户隔离待 v2.0 启用） */
    private String tenantId;

    /** 生命周期状态 */
    private String status;

    /** 生效时间 */
    private LocalDateTime effectiveFrom;

    /** 失效时间 */
    private LocalDateTime effectiveTo;

    /** 审核人 */
    private String reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 审核意见 */
    private String reviewComment;

    /**
     * 灰度比例（0.0~1.0，0 表示不启用灰度；P1-10 AB Test 自动回滚用）
     */
    private Double canaryRatio;

    /** 灰度条件表达式列表（JSON 数组） */
    private String canaryConditions;

    /** 灰度候选版本条件表达式 */
    private String canaryConditionExpression;

    /** 灰度候选版本严重度表达式 */
    private String canarySeverityExpression;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
