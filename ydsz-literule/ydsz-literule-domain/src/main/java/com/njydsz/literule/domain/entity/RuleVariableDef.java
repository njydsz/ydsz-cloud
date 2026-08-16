package com.njydsz.literule.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 规则变量定义 DO
 *
 * <p>映射 ydsz_rule_variable_def 表，存储规则表达式中可引用的变量元数据。
 * 由 {@link com.njydsz.project.literule.DatabaseVariableRegistry} 加载，
 * 供 {@link com.njydsz.literule.server.expression.ExpressionValidationService} 做 UNDEFINED_VARIABLE 校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_rule_variable_def")
public class RuleVariableDef extends MpBaseEntity<String> {

    /** 变量名（如 cpi / budgetAmount / evmRedCount） */
    private String varName;

    /** 变量类型（Number / String 等） */
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

}
