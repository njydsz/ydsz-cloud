package com.njydsz.pmis.common.jdbc.enums;

/**
 * 字段填充策略枚举，定义字段在何时进行填充
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FieldFillStrategyEnum {

    /**
     * 插入时填充字段
     */
    INSERT,

    /**
     * 更新时填充字段
     */
    UPDATE,

    /**
     * 插入和更新时都填充字段
     */
    INSERT_UPDATE
}
