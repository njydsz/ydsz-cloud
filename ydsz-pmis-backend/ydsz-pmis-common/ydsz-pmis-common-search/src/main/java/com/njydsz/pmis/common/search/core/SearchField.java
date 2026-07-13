package com.njydsz.pmis.common.search.core;

/**
 * 搜索字段配置
 * 定义不同字段在全文检索中的权重影响
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public enum SearchField {

    /**
     * 标题字段 - 权重A (最重要)
     */
    TITLE('A'),
    /**
     * 副标题字段 - 权重B
     */
    SUBTITLE('B'),
    /**
     * 内容字段 - 权重C
     */
    CONTENT('C'),
    /**
     * 标签字段 - 权重D (最次要)
     */
    TAG('D'),
    /**
     * 默认为C权重
     */
    DEFAULT('C');

    private final char weight;

    SearchField(char weight) {
        this.weight = weight;
    }

    public char getWeight() {
        return weight;
    }

    /**
     * 将Char类型的权重转换为PostgreSQL ts_rank可识别的浮点值
     * @see <a href="https://www.postgresql.org/docs/current/textsearch-controls.html#TEXTSEARCH-RANKING">PostgreSQL ts_rank文档</a>
     */
    public static float getWeightValue(char weight) {
        return switch (weight) {
            case 'A' -> 1.0f;   // 最重要
            case 'B' -> 0.4f;   // 重要
            case 'C' -> 0.2f;   // 普通
            case 'D' -> 0.1f;   // 次要
            default -> 0.2f;    // 默认
        };
    }
}