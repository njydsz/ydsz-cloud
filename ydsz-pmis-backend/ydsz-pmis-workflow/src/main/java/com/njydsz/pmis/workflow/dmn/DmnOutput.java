package com.njydsz.pmis.workflow.dmn;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * DMN 输出列定义
 *
 * <p>描述决策表的一个输出列：
 * <ul>
 *   <li>{@code name} — 输出字段名</li>
 *   <li>{@code label} — 显示名称</li>
 *   <li>{@code type} — 字段类型: STRING/NUMBER/BOOLEAN/DATE</li>
 *   <li>{@code allowedValues} — P2-10: 输出值允许列表（有序，前面的优先级更高）
 *       <p>用于 {@link DmnHitPolicy#PRIORITY} / {@link DmnHitPolicy#OUTPUT_ORDER} 命中策略：
 *       命中行按其输出值在 {@code allowedValues} 中的索引升序排序，索引靠前优先级高。
 *       未在列表中的输出值视为最低优先级（按命中顺序稳定排序）。</li>
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

    /**
     * P2-10: 输出值允许列表（有序，前面的优先级更高）。
     *
     * <p>用于 PRIORITY / OUTPUT_ORDER 命中策略：
     * <ul>
     *   <li>PRIORITY：按首个输出列的 allowedValues 排序，返回排名第一的命中行</li>
     *   <li>OUTPUT_ORDER：按各输出列的 allowedValues 依次排序，返回所有命中行</li>
     * </ul>
     *
     * <p>示例：{@code ["高", "中", "低"]} 表示"高"优先级最高。
     * 未在列表中的输出值视为最低优先级（稳定排序保留命中顺序）。
     */
    private List<String> allowedValues;
}
