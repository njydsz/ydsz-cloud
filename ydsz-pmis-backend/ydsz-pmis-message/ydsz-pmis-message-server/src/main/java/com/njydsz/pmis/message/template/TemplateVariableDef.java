package com.njydsz.pmis.message.server.template;

import lombok.Data;

import java.util.List;

/**
 * 模板变量定义（P0-3）。
 *
 * <p>描述模板中每个变量的类型、是否必填、默认值、枚举可选值等元信息，
 * 用于渲染前自动校验 + 前端可视化编辑器渲染表单。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Data
public class TemplateVariableDef {

    /** 变量名（与模板中 ${varName} 对应） */
    private String name;

    /** 变量类型 */
    private VariableType type;

    /** 是否必填 */
    private boolean required;

    /** 默认值（params 缺失时使用，可为 null） */
    private String defaultValue;

    /** 枚举可选值（type=ENUM 时生效） */
    private List<String> enumValues;

    /** 描述说明（前端展示） */
    private String description;

    /** 示例值（前端展示） */
    private String example;

    /**
     * 模板变量类型枚举。
     */
    public enum VariableType {
        /** 字符串 */
        STRING,
        /** 数字(整数/小数) */
        NUMBER,
        /** 布尔 */
        BOOLEAN,
        /** 日期(yyyy-MM-dd) */
        DATE,
        /** 日期时间(yyyy-MM-dd HH:mm:ss) */
        DATETIME,
        /** 枚举(从 enumValues 中取值) */
        ENUM,
        /** 列表(渲染时 toString) */
        LIST
    }
}
