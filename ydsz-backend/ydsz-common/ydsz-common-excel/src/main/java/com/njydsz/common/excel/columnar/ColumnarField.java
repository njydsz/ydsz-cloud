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

    /**
     * 列名，在同一 {@link ColumnarSchema} 内唯一。
     *
     * @return 列名，构造期已校验，永不为 {@code null}
     */
    public String name() {
        return name;
    }

    /**
     * 列的逻辑类型，决定写入时映射到的 Parquet/ORC 物理类型。
     *
     * @return 列类型，构造期已校验，永不为 {@code null}
     */
    public ColumnarType type() {
        return type;
    }

    /**
     * 该列是否允许 {@code null} 值。
     *
     * <p>为 {@code false} 时会写入为 required 列，写入 {@code null} 将由底层 writer 报错。
     *
     * @return {@code true} 表示可空
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * DECIMAL 的总有效位数。
     *
     * @return 精度；仅 {@link ColumnarType#DECIMAL} 有意义（构造期未显式指定时归一为 18），
     *         其他类型固定为 0
     */
    public int precision() {
        return precision;
    }

    /**
     * DECIMAL 的小数位数。
     *
     * @return 小数位数；仅 {@link ColumnarType#DECIMAL} 有意义且不超过 {@link #precision()}，
     *         其他类型固定为 0
     */
    public int scale() {
        return scale;
    }

    /**
     * 列注释，用于写入文件元数据以便下游数仓识别业务含义。
     *
     * @return 列注释；未设置时返回 {@code null}
     */
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

        /**
         * 设置该列是否可空，默认可空（{@code true}）。
         *
         * <p>置为 {@code false} 会生成 required 列，一旦实际数据出现 {@code null}，
         * 写入阶段即失败，因此仅对确有非空约束的列关闭。
         *
         * @param nullable {@code true} 表示可空
         * @return 当前构建器，便于链式调用
         */
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

        /**
         * 设置列注释，写入文件元数据供下游数仓识别业务含义。
         *
         * <p>不参与 {@link ColumnarField#equals(Object)} 与 {@code hashCode} 比较，
         * 即仅注释不同的两个字段视为等价。
         *
         * @param comment 列注释，可为 {@code null} 表示不写注释
         * @return 当前构建器，便于链式调用
         */
        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        /**
         * 构建字段元数据。
         *
         * <p>对 DECIMAL 类型执行归一与校验：{@code precision <= 0} 归一为 18，
         * 负 {@code scale} 归一为 0。
         *
         * @return 不可变的字段元数据
         * @throws IllegalArgumentException 当 DECIMAL 的 {@code scale} 超过 {@code precision} 时抛出
         */
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
