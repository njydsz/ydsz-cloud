package com.njydsz.pmis.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 规则变量定义 DO
 *
 * <p>映射 pmis_rule_variable_def 表，存储规则表达式中可引用的变量元数据。
 * 由 {@link com.njydsz.pmis.project.literule.DatabaseVariableRegistry} 加载，
 * 供 {@link com.njydsz.pmis.literule.expr.ExpressionValidationService} 做 UNDEFINED_VARIABLE 校验。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Data
@TableName("pmis_rule_variable_def")
public class RuleVariableDefDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 变量名（如 cpi / budgetAmount / evmRedCount） */
    private String varName;

    /** 变量类型（java.lang.Number / java.lang.String 等） */
    private String varType;

    /** 变量描述（中文，供前端编辑器提示） */
    private String description;

    /** 示例值（TEXT，存储为字符串，用于前端编辑器预览和 dryRun 默认 facts） */
    private String sampleValue;

    /** 变量来源类别（EVM / PROJECT / FINANCE / BENCH 等） */
    private String category;

    /** 是否必填 */
    private Boolean required;

    /** 是否启用 */
    private Boolean enabled;

    /** 租户 ID（单租户部署默认 1） */
    private String tenantId;

    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}
