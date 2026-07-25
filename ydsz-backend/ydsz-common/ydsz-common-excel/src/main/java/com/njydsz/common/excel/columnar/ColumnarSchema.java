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

    public List<ColumnarField> fields() {
        return fields;
    }

    public List<String> headerNames() {
        return headerNames;
    }

    public int fieldCount() {
        return fields.size();
    }

    public ColumnarField field(int index) {
        return fields.get(index);
    }

    public ColumnarField field(String name) {
        Integer idx = indexMap.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("Field not found in schema: " + name);
        }
        return fields.get(idx);
    }

    public int fieldIndex(String name) {
        Integer idx = indexMap.get(name);
        if (idx == null) {
            throw new IllegalArgumentException("Field not found in schema: " + name);
        }
        return idx;
    }

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

        public Builder addField(ColumnarField field) {
            Objects.requireNonNull(field, "field must not be null");
            fields.add(field);
            return this;
        }

        public Builder addFields(ColumnarField... fs) {
            for (ColumnarField f : fs) {
                addField(f);
            }
            return this;
        }

        public Builder addFields(List<ColumnarField> fs) {
            for (ColumnarField f : fs) {
                addField(f);
            }
            return this;
        }

        public ColumnarSchema build() {
            if (fields.isEmpty()) {
                throw new IllegalStateException("ColumnarSchema must have at least one field");
            }
            return new ColumnarSchema(fields);
        }
    }
}
