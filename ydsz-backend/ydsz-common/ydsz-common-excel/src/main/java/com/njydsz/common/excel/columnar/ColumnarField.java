package com.njydsz.common.excel.columnar;

import java.util.Objects;

/**
 * 列式存储字段元数据。
 *
 * <p>描述 Parquet/ORC 表中一列的名称、类型、是否可空、精度等元数据。
 * 与 {@link ColumnarSchema} 配合使用，构成列式存储的逻辑表结构。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ColumnarField id = ColumnarField.of("id", ColumnarType.INT64, false);
 * ColumnarField name = ColumnarField.of("name", ColumnarType.STRING, true);
 * ColumnarField amount = ColumnarField.builder("amount", ColumnarType.DECIMAL)
 *     .precision(18)
 *     .scale(2)
 *     .build();
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ColumnarField {

    private final String name;
    private final ColumnarType type;
    private final boolean nullable;
    private final int precision;
    private final int scale;
    private final String comment;

    private ColumnarField(Builder b) {
        this.name = Objects.requireNonNull(b.name, "name must not be null");
        this.type = Objects.requireNonNull(b.type, "type must not be null");
        this.nullable = b.nullable;
        this.precision = b.precision;
        this.scale = b.scale;
        this.comment = b.comment;
    }

    /**
     * 创建必填字段。
     */
    public static ColumnarField of(String name, ColumnarType type) {
        return builder(name, type).build();
    }

    /**
     * 创建字段（指定可空性）。
     */
    public static ColumnarField of(String name, ColumnarType type, boolean nullable) {
        return builder(name, type).nullable(nullable).build();
    }

    public static Builder builder(String name, ColumnarType type) {
        return new Builder(name, type);
    }

    public String name() {
        return name;
    }

    public ColumnarType type() {
        return type;
    }

    public boolean isNullable() {
        return nullable;
    }

    public int precision() {
        return precision;
    }

    public int scale() {
        return scale;
    }

    public String comment() {
        return comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ColumnarField)) {
            return false;
        }
        ColumnarField that = (ColumnarField) o;
        return nullable == that.nullable
                && precision == that.precision
                && scale == that.scale
                && name.equals(that.name)
                && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, nullable, precision, scale);
    }

    @Override
    public String toString() {
        return "ColumnarField{" + name + ":" + type
                + (nullable ? "?" : "")
                + (type == ColumnarType.DECIMAL ? "(" + precision + "," + scale + ")" : "")
                + (comment != null ? " // " + comment : "")
                + "}";
    }

    /**
     * 字段构造器。
     */
    public static final class Builder {
        private final String name;
        private final ColumnarType type;
        private boolean nullable = true;
        private int precision = 0;
        private int scale = 0;
        private String comment;

        private Builder(String name, ColumnarType type) {
            this.name = name;
            this.type = type;
        }

        public Builder nullable(boolean nullable) {
            this.nullable = nullable;
            return this;
        }

        /**
         * 精度（仅 DECIMAL 类型有效，默认 18）。
         */
        public Builder precision(int precision) {
            this.precision = precision;
            return this;
        }

        /**
         * 小数位数（仅 DECIMAL 类型有效，默认 2）。
         */
        public Builder scale(int scale) {
            this.scale = scale;
            return this;
        }

        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public ColumnarField build() {
            if (type == ColumnarType.DECIMAL) {
                if (precision <= 0) {
                    precision = 18;
                }
                if (scale < 0) {
                    scale = 0;
                }
                if (scale > precision) {
                    throw new IllegalArgumentException(
                            "DECIMAL scale (" + scale + ") must not exceed precision (" + precision + ")");
                }
            }
            return new ColumnarField(this);
        }
    }
}
