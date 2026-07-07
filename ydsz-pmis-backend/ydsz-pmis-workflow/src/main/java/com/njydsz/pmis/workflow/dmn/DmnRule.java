package com.njydsz.pmis.workflow.dmn;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * DMN 规则行
 *
 * <p>描述决策表的一行规则：
 * <ul>
 *   <li>{@code inputEntries} — 输入条件表达式列表，与决策表 inputs 一一对应</li>
 *   <li>{@code outputEntries} — 输出值列表，与决策表 outputs 一一对应</li>
 *   <li>{@code description} — 规则描述</li>
 * </ul>
 *
 * <p>条件表达式示例：
 * <ul>
 *   <li>{@code ">100"} — 数值大于</li>
 *   <li>{@code "==true"} — 布尔等于</li>
 *   <li>{@code "'紧急'"} — 字符串等于</li>
 *   <li>{@code "-"} 或空 — 任意匹配（always true）</li>
 * </ul>
 *
 * <p>输出值示例：
 * <ul>
 *   <li>{@code "'通过'"} — 字符串输出</li>
 *   <li>{@code "100"} — 数值输出</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Data
public class DmnRule implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 输入条件表达式列表（与决策表 inputs 一一对应） */
    private List<String> inputEntries;

    /** 输出值列表（与决策表 outputs 一一对应） */
    private List<String> outputEntries;

    /** 规则描述 */
    private String description;
}
