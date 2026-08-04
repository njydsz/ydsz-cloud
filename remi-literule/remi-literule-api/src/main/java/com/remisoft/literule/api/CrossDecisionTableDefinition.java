package com.remisoft.literule.api;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交叉决策表定义（决策矩阵，P1-6）
 *
 * <p>对标 URule Pro 的交叉决策表（决策矩阵），支持行和列双维度交叉匹配。
 *
 * <p>与普通决策表（{@link DecisionTableDefinition}）的区别：
 * <ul>
 *   <li>普通决策表：行 = 规则，列 = 条件，每行条件 AND 关系</li>
 *   <li>交叉决策表：行和列都是条件维度，交叉单元格 = 动作输出</li>
 * </ul>
 *
 * <p>适用场景：费率表、税率表、运费表、风险等级矩阵等二维表格决策。
 *
 * <p>结构示例（风险等级矩阵）：
 * <pre>
 *  rowDimension: "evmRedCount"（行维度：EVM 红灯数）
 *  columnDimension: "grossMargin"（列维度：毛利率）
 *
 *              grossMargin < 0.05   grossMargin [0.05, 0.15)   grossMargin >= 0.15
 *  evmRed >= 3   RED（高风险）          RED（高风险）              YELLOW（中风险）
 *  evmRed 1~2    YELLOW（中风险）       YELLOW（中风险）           INFO（正常）
 *  evmRed 0      INFO（正常）           INFO（正常）              INFO（正常）
 * </pre>
 *
 * <p>JSON 结构：
 * <pre>
 * {
 *   "matrixCode": "MTX_RISK",
 *   "matrixName": "风险等级矩阵",
 *   "rowDimension": "evmRedCount",
 *   "columnDimension": "grossMargin",
 *   "rowBuckets": [
 *     {"label":"EVM红灯>=3", "condition":">=3"},
 *     {"label":"EVM红灯1~2", "condition":"[1,3)"},
 *     {"label":"EVM红灯0", "condition":"0"}
 *   ],
 *   "columnBuckets": [
 *     {"label":"毛利率<0.05", "condition":"<0.05"},
 *     {"label":"毛利率0.05~0.15", "condition":"[0.05,0.15)"},
 *     {"label":"毛利率>=0.15", "condition":">=0.15"}
 *   ],
 *   "cells": {
 *     "0_0": {"severity":"RED","title":"高风险"},
 *     "0_1": {"severity":"RED","title":"高风险"},
 *     "0_2": {"severity":"YELLOW","title":"中风险"},
 *     "1_0": {"severity":"YELLOW","title":"中风险"},
 *     ...
 *   },
 *   "defaultActions": {"severity":"INFO","title":"正常"}
 * }
 * </pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrossDecisionTableDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 矩阵编码（唯一） */
    private String matrixCode;

    /** 矩阵名称 */
    private String matrixName;

    /** 描述 */
    private String description;

    /** 类别 */
    private String category;

    /**
     * 行维度字段名（从 facts 中取值的键名）
     *
     * <p>例如 "evmRedCount" 表示从 facts.get("evmRedCount") 获取行维度值
     */
    private String rowDimension;

    /**
     * 列维度字段名（从 facts 中取值的键名）
     *
     * <p>例如 "grossMargin" 表示从 facts.get("grossMargin") 获取列维度值
     */
    private String columnDimension;

    /**
     * 行分桶列表（按优先级匹配，首个命中的桶确定行索引）
     *
     * <p>每个桶定义一个条件表达式，命中后该行作为交叉匹配的行索引
     */
    private List<Bucket> rowBuckets;

    /**
     * 列分桶列表（按优先级匹配，首个命中的桶确定列索引）
     */
    private List<Bucket> columnBuckets;

    /**
     * 交叉单元格动作映射
     *
     * <p>key 格式为 "rowIndex_columnIndex"（如 "0_1"），value 为动作映射
     */
    private Map<String, Map<String, Object>> cells;

    /** 默认动作（行或列未匹配到桶时使用） */
    private Map<String, Object> defaultActions;

    /** 是否启用 */
    @Builder.Default
    private boolean enabled = true;

    /** 优先级 */
    @Builder.Default
    private int priority = Rule.DEFAULT_PRIORITY;

    /** 影响范围 */
    private String scope;

    /** 版本号 */
    @Builder.Default
    private int version = 1;

    /**
     * 分桶定义
     *
     * <p>一个分桶代表一个条件区间，从 facts 中取维度值后按桶顺序匹配，
     * 首个命中的桶确定行/列索引。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 桶显示名（如 "EVM红灯>=3"） */
        private String label;

        /**
         * 条件表达式
         * <p>支持与决策表条件相同的格式：字面值/比较表达式/区间/枚举
         * <p>例如：">=3" / "[1,3)" / "RED|YELLOW" / "0"
         */
        private String condition;
    }

    /**
     * 构建单元格 key
     *
     * @param rowIndex    行索引
     * @param columnIndex 列索引
     * @return 单元格 key（如 "0_1"）
     */
    public static String cellKey(int rowIndex, int columnIndex) {
        return rowIndex + "_" + columnIndex;
    }
}
