package com.njydsz.common.excel.columnar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 列式存储表结构（Schema）。
 *
 * <p>由若干 {@link ColumnarField} 组成的有序列表，描述 Parquet/ORC 表的逻辑结构。
 * 列顺序与数据行/对象属性顺序一致。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ColumnarSchema schema = ColumnarSchema.builder()
 *     .addField(ColumnarField.of("id", ColumnarType.INT64, false))
 *     .addField(ColumnarField.of("name", ColumnarType.STRING, true))
 *     .addField(ColumnarField.builder("amount", ColumnarType.DECIMAL)
 *         .precision(18).scale(2).build())
 *     .build();
 *
 * List<String> headers = schema.headerNames();
 * int idx = schema.fieldIndex("name");
 * ColumnarField f = schema.field("amount");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ColumnarSchema {

    private final List<ColumnarField> fields;
    private final Map<String, Integer> indexMap;
    private final List<String> headerNames;

    private ColumnarSchema(List<ColumnarField> fields) {
        this.fields = List.copyOf(fields);
        this.indexMap = new LinkedHashMap<>(fields.size() * 2);
        List<String> headers = new ArrayList<>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            ColumnarField f = fields.get(i);
            if (indexMap.putIfAbsent(f.name(), i) != null) {
                throw new IllegalArgumentException(
                        "Duplicate field name in columnar schema: " + f.name());
            }
            headers.add(f.name());
        }
        this.headerNames = Collections.unmodifiableList(headers);
    }

    /**
     * 创建 Schema 构建器。
     *
     * <p>构建器初始为空，需先 {@link Builder#addField} 添加至少一个字段再调用
     * {@link Builder#build()}，否则构建时会抛 {@link IllegalStateException}。
     *
     * @return 空的 Schema 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从字段列表创建 Schema。
     */
    public static ColumnarSchema of(ColumnarField... fields) {
        return builder().addFields(fields).build();
    }

    /**
     * 从字段列表创建 Schema。
     */
    public static ColumnarSchema of(List<ColumnarField> fields) {
        return builder().addFields(fields).build();
    }

    /**
     * 全部字段，顺序即列顺序，与数据行的取值顺序严格对应。
     *
     * @return 不可变字段列表，至少含一个元素；尝试修改会抛 {@link UnsupportedOperationException}
     */
    public List<ColumnarField> fields() {
        return fields;
    }

    /**
     * 按列顺序返回列名，供导出表头或与 Excel 表头对齐时使用。
     *
     * @return 不可变列名列表，顺序与 {@link #fields()} 一致
     */
    public List<String> headerNames() {
        return headerNames;
    }

    /**
     * 列数量。
     *
     * @return 列数量，恒 &gt;= 1（构建期已保证 Schema 非空）
     */
    public int fieldCount() {
        return fields.size();
    }

    /**
     * 按下标获取字段。
     *
     * @param index 列下标，取值范围 {@code [0, fieldCount())}
     * @return 对应字段，永不为 {@code null}
     * @throws IndexOutOfBoundsException 当下标越界时抛出
     */
    public ColumnarField field(int index) {
        return fields.get(index);
    }

    /**
     * 按列名获取字段。
     *
     * <p>基于构建期预建的名称索引查找，时间复杂度 O(1)。名称大小写敏感。
     * 若调用方无法确保列存在，应先用 {@link #hasField(String)} 判断以避免异常。
     *
     * @param name 列名，大小写敏感
     * @return 对应字段，永不为 {@code null}
     * @throws IllegalArgumentException 当 Schema 中不存在该列名时抛出
     */
    public ColumnarField field(String name) {
        Integer idx = indexMap.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("Field not found in schema: " + name);
        }
        return fields.get(idx);
    }

    /**
     * 按列名查询列下标，用于将命名访问转换为按位访问以提升批量读写性能。
     *
     * @param name 列名，大小写敏感
     * @return 列下标，从 0 开始
     * @throws IllegalArgumentException 当 Schema 中不存在该列名时抛出
     */
    public int fieldIndex(String name) {
        Integer idx = indexMap.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("Field not found in schema: " + name);
        }
        return idx;
    }

    /**
     * 判断 Schema 中是否包含指定列名。
     *
     * <p>基于构建期预建的名称索引查找，时间复杂度 O(1)，名称大小写敏感。
     * 供调用方在调用可能抛异常的 {@link #field(String)} / {@link #fieldIndex(String)}
     * 之前做存在性预检，适配动态表头场景。
     *
     * @param name 列名，大小写敏感；可为 {@code null}（此时返回 {@code false}）
     * @return {@code true} 表示 Schema 中存在该列
     */
    public boolean hasField(String name) {
        return indexMap.containsKey(name);
    }

    @Override
    public String toString() {
        return "ColumnarSchema" + fields;
    }

    /**
     * Schema 构造器。
     */
    public static final class Builder {
        private final List<ColumnarField> fields = new ArrayList<>();

        /**
         * 追加一个字段，添加顺序即最终列顺序。
         *
         * <p>此处不做重名检查，重名冲突延迟到 {@link #build()} 统一暴露。
         *
         * @param field 字段元数据，不可为 {@code null}
         * @return 当前构建器，便于链式调用
         * @throws NullPointerException 当 {@code field} 为 {@code null} 时抛出
         */
        public Builder addField(ColumnarField field) {
            Objects.requireNonNull(field, "field must not be null");
            fields.add(field);
            return this;
        }

        /**
         * 批量追加字段，按数组顺序依次入列。
         *
         * <p>非原子操作：中途遇到 {@code null} 元素抛出后，此前已追加的字段仍保留在构建器中。
         *
         * @param fs 字段数组，数组本身及元素均不可为 {@code null}
         * @return 当前构建器，便于链式调用
         * @throws NullPointerException 当数组为 {@code null} 或含 {@code null} 元素时抛出
         */
        public Builder addFields(ColumnarField... fs) {
            for (ColumnarField f : fs) {
                addField(f);
            }
            return this;
        }

        /**
         * 批量追加字段，按列表迭代顺序依次入列。
         *
         * <p>非原子操作：中途遇到 {@code null} 元素抛出后，此前已追加的字段仍保留在构建器中。
         *
         * @param fs 字段列表，列表本身及元素均不可为 {@code null}
         * @return 当前构建器，便于链式调用
         * @throws NullPointerException 当列表为 {@code null} 或含 {@code null} 元素时抛出
         */
        public Builder addFields(List<ColumnarField> fs) {
            for (ColumnarField f : fs) {
                addField(f);
            }
            return this;
        }

        /**
         * 构建 Schema，并在此统一完成非空与列名唯一性校验。
         *
         * @return 不可变的 Schema 实例
         * @throws IllegalStateException 当一个字段都未添加时抛出
         * @throws IllegalArgumentException 当存在重复列名时抛出
         */
        public ColumnarSchema build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("ColumnarSchema must have at least one field");
            }
            return new ColumnarSchema(fields);
        }
    }
}
