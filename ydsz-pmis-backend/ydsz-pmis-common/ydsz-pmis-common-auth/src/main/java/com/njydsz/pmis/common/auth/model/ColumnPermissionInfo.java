package com.njydsz.pmis.common.auth.model;

import java.util.*;

/**
 * 列权限信息集合。
 *
 * <p>以 Map 形式存储和管理多个字段的列权限信息，
 * 提供权限的添加、合并、查询等操作。
 *
 * <p><b>与 ColumnScopeInfo 的区别：</b>
 * <ul>
 *   <li>{@link ColumnScopeInfo}：按表组织，关注的是"哪些表有哪些可见/可编辑字段"</li>
 *   <li>{@link ColumnPermissionInfo}：按字段组织，关注的是"某个字段是否可读/可写"</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 * @see ColumnPermission
 * @see ColumnScopeInfo
 */
public class ColumnPermissionInfo {

    private final Map<String, ColumnPermission> columnMap;

    /**
     * 构造空的列权限信息集合。
     */
    public ColumnPermissionInfo() {
        this.columnMap = new LinkedHashMap<>();
    }

    /**
     * 根据已有列权限映射构造列权限信息集合。
     *
     * @param columnMap 列名到权限的映射
     */
    public ColumnPermissionInfo(Map<String, ColumnPermission> columnMap) {
        this.columnMap = new LinkedHashMap<>(columnMap);
    }

    /**
     * 创建一个空的列权限信息集合。
     *
     * @return 空的列权限信息实例
     */
    public static ColumnPermissionInfo empty() {
        return new ColumnPermissionInfo(Collections.emptyMap());
    }

    /**
     * 添加单个字段的列权限。
     *
     * <p>如果该字段已存在权限，则与现有权限合并（取并集）。
     * 列名会自动转为小写进行标准化。
     *
     * @param column 字段名
     * @param permission 列权限
     */
    public void add(String column, ColumnPermission permission) {
        if (column == null || permission == null) {
            return;
        }
        String normalizedColumn = column.trim().toLowerCase(Locale.ROOT);
        ColumnPermission existing = columnMap.get(normalizedColumn);
        if (existing == null) {
            columnMap.put(normalizedColumn, permission);
            return;
        }
        columnMap.put(normalizedColumn, existing.merge(permission));
    }

    /**
     * 批量添加列权限。
     *
     * @param other 列名到权限的映射
     */
    public void addAll(Map<String, ColumnPermission> other) {
        if (other == null || other.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ColumnPermission> e : other.entrySet()) {
            add(e.getKey(), e.getValue());
        }
    }

    /**
     * 获取指定字段的列权限。
     *
     * @param column 字段名（不区分大小写）
     * @return 列权限，字段不存在时返回 {@code null}
     */
    public ColumnPermission get(String column) {
        if (column == null) {
            return null;
        }
        return columnMap.get(column.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 获取所有可读字段的名称集合。
     *
     * @return 可读字段名称集合
     */
    public Set<String> getReadableColumns() {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, ColumnPermission> e : columnMap.entrySet()) {
            if (e.getValue().isReadable()) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * 获取所有可写字段的名称集合。
     *
     * @return 可写字段名称集合
     */
    public Set<String> getWritableColumns() {
        Set<String> result = new LinkedHashSet<>();
        for (Map.Entry<String, ColumnPermission> e : columnMap.entrySet()) {
            if (e.getValue().isWritable()) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    /**
     * 获取所有字段名称集合。
     *
     * @return 不可变的字段名称集合
     */
    public Set<String> getAllColumns() {
        return Collections.unmodifiableSet(columnMap.keySet());
    }

    /**
     * 判断列权限信息是否为空。
     *
     * @return 为空时返回 {@code true}
     */
    public boolean isEmpty() {
        return columnMap.isEmpty();
    }

    /**
     * 获取字段数量。
     *
     * @return 字段数量
     */
    public int size() {
        return columnMap.size();
    }

    /**
     * 判断指定字段是否可读。
     *
     * @param column 字段名（不区分大小写）
     * @return 可读时返回 {@code true}
     */
    public boolean isColumnReadable(String column) {
        ColumnPermission p = get(column);
        return p != null && p.isReadable();
    }

    /**
     * 判断指定字段是否可写。
     *
     * @param column 字段名（不区分大小写）
     * @return 可写时返回 {@code true}
     */
    public boolean isColumnWritable(String column) {
        ColumnPermission p = get(column);
        return p != null && p.isWritable();
    }
}