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
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ruleCode;
    private String ruleName;
    private String category;
    private String description;
    private String conditionExpression;
    private String severityExpression;
    private String defaultSeverity;
    private String titleTemplate;
    private String descriptionTemplate;
    private Integer priority;
    private Boolean enabled;
    private String scope;
    private Boolean drilldownAvailable;
    private Integer version;

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

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
