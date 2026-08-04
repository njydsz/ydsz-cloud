package com.njydsz.common.excel.columnar;

import java.util.Optional;

/**
 * 列式存储行映射器。
 *
 * <p>与 {@link com.njydsz.common.excel.tabular.TabularRowMapper} 的差异：
 * ① 输入/输出是 {@code Object[]}（按 Schema 字段类型直接传递），而不是 {@code String[]}，
 *   避免 Parquet/ORC 类型到 String 的反复转换；② 必须配合 {@link ColumnarSchema} 使用。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ColumnarSchema schema = ColumnarSchema.builder()
 *     .addField(ColumnarField.of("id", ColumnarType.INT64, false))
 *     .addField(ColumnarField.of("name", ColumnarType.STRING, true))
 *     .build();
 *
 * ColumnarRowMapper<User> mapper = new ColumnarRowMapper<User>() {
 *     public Object[] toRow(User u) { return new Object[]{u.getId(), u.getName()}; }
 *     public User fromRow(Object[] row) { return new User((Long)row[0], (String)row[1]); }
 * };
 * }</pre>
 *
 * @param <T> 目标对象类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ColumnarRowMapper<T> {

    /**
     * 将 Java 对象转换为原始行数据（按 Schema 字段顺序）。
     *
     * @param object Java 对象
     * @return 与 {@link ColumnarSchema} 字段顺序一致的 Object 数组
     */
    Object[] toRow(T object);

    /**
     * 将原始行数据（按 Schema 字段顺序）转换为 Java 对象。
     *
     * @param values 原始行数据（与 {@link ColumnarSchema} 字段顺序一致）
     * @return 映射后的对象
     */
    T fromRow(Object[] values);

    /**
     * 关联的 Schema（用于类型校验）。
     */
    default Optional<ColumnarSchema> schema() {
        return Optional.empty();
    }
}
