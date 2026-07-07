package com.njydsz.pmis.workflow.dmn;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DMN 输入列定义
 *
 * <p>描述决策表的一个输入列：
 * <ul>
 *   <li>{@code name} — 输入字段名（对应上下文变量 key）</li>
 *   <li>{@code label} — 显示名称</li>
 *   <li>{@code type} — 字段类型: STRING/NUMBER/BOOLEAN/DATE</li>
 *   <li>{@code expression} — 取值表达式（取值时优先使用，为空则用 name 作为 key）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
public class DmnInput implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 输入字段名（对应上下文变量 key） */
    private String name;

    /** 显示名称 */
    private String label;

    /** 字段类型: STRING/NUMBER/BOOLEAN/DATE */
    private String type;

    /** 取值表达式（取值时优先使用，为空则用 name 作为 key） */
    private String expression;
}
