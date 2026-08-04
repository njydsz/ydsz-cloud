package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * 变量定义视图对象（VO）。
 *
 * <p>用于前端展示规则表达式中可引用变量的元信息（名称、类型、示例值、分类），
 * 支撑规则编辑时的变量提示与校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class VariableDefinitionVO {

    /** 变量名称（表达式中引用的标识） */
    private String name;

    /** 变量类型（STRING/NUMBER/BOOLEAN/DATE 等） */
    private String type;

    /** 变量描述（中文说明） */
    private String description;

    /** 示例值（用于前端预览与默认值提示） */
    private Object sampleValue;

    /** 变量分类（来源模块，如 EVM/PROJECT/FINANCE） */
    private String category;

}
