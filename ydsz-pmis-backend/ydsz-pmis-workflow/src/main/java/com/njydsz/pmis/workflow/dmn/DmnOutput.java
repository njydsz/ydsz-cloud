package com.njydsz.pmis.workflow.dmn;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * DMN 输出列定义
 *
 * <p>描述决策表的一个输出列：
 * <ul>
 *   <li>{@code name} — 输出字段名</li>
 *   <li>{@code label} — 显示名称</li>
 *   <li>{@code type} — 字段类型: STRING/NUMBER/BOOLEAN/DATE</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
public class DmnOutput implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 输出字段名 */
    private String name;

    /** 显示名称 */
    private String label;

    /** 字段类型: STRING/NUMBER/BOOLEAN/DATE */
    private String type;
}
