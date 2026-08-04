package com.remisoft.common.auth.model;

/**
 * 单个字段的列权限定义。
 *
 * <p>描述某个字段的读/写权限状态：
 * <ul>
 *   <li>readable：是否可读</li>
 *   <li>writable：是否可写</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 * @see ColumnPermissionInfo
 */
public final class ColumnPermission {

    private final String column;
    private final boolean readable;
    private final boolean writable;

    /**
     * 构造列权限。
     *
     * @param column 字段名
     * @param readable 是否可读
     * @param writable 是否可写
     */
    public ColumnPermission(String column, boolean readable, boolean writable) {
        this.column = column;
        this.readable = readable;
        this.writable = writable;
    }

    /**
     * 获取字段名。
     *
     * @return 字段名
     */
    public String getColumn() {
        return column;
    }

    /**
     * 判断字段是否可读。
     *
     * @return 可读时返回 {@code true}
     */
    public boolean isReadable() {
        return readable;
    }

    /**
     * 判断字段是否可写。
     *
     * @return 可写时返回 {@code true}
     */
    public boolean isWritable() {
        return writable;
    }

    /**
     * 创建只读权限的列权限实例。
     *
     * @param column 字段名
     * @return 只读列权限
     */
    public static ColumnPermission readOnly(String column) {
        return new ColumnPermission(column, true, false);
    }

    /**
     * 创建读写权限的列权限实例。
     *
     * @param column 字段名
     * @return 读写列权限
     */
    public static ColumnPermission readWrite(String column) {
        return new ColumnPermission(column, true, true);
    }

    /**
     * 创建隐藏权限的列权限实例（不可读不可写）。
     *
     * @param column 字段名
     * @return 隐藏列权限
     */
    public static ColumnPermission hidden(String column) {
        return new ColumnPermission(column, false, false);
    }

    /**
     * 合并两个列权限，取并集（任一可读则可读，任一可写则可写）。
     *
     * @param other 另一个列权限
     * @return 合并后的列权限
     */
    public ColumnPermission merge(ColumnPermission other) {
        if (other == null) {
            return this;
        }
        return new ColumnPermission(
                this.column,
                this.readable || other.readable,
                this.writable || other.writable
        );
    }

    @Override
    public String toString() {
        return "ColumnPermission{" +
                "column='" + column + '\'' +
                ", readable=" + readable +
                ", writable=" + writable +
                '}';
    }
}