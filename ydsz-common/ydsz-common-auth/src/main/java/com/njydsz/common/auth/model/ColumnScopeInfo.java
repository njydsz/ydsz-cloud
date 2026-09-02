package com.njydsz.common.auth.model;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import lombok.Getter;

import com.njydsz.common.auth.annotation.AuthColPermission;
import com.njydsz.common.auth.service.ColumnPermissionResolver;

/**
 * 列权限信息载体。
 *
 * <p>用于表达"当前用户可读/可写的字段集合"，以表为单位进行组织。
 *
 * <p><b>数据结构：</b>
 *
 * <ul>
 *   <li>visibleColumnsByTable：表名到可见字段集合的映射（读权限）
 *   <li>editableColumnsByTable：表名到可编辑字段集合的映射（写权限）
 * </ul>
 *
 * <p><b>与 SQL 拦截器联动：</b>
 *
 * <p>列权限信息会通过 header（X-Visible-Columns、X-Editable-Columns）透传到下游服务， SQL 拦截器根据这些信息自动在
 * SELECT/INSERT/UPDATE 语句中过滤无权访问的字段。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see AuthColPermission
 * @see ColumnPermissionResolver
 */
@Getter
public class ColumnScopeInfo {

  private final Map<String, Set<String>> visibleColumnsByTable;
  private final Map<String, Set<String>> editableColumnsByTable;

  public ColumnScopeInfo(
      Map<String, Set<String>> visibleColumnsByTable,
      Map<String, Set<String>> editableColumnsByTable) {
    this.visibleColumnsByTable =
        visibleColumnsByTable != null
            ? Collections.unmodifiableMap(visibleColumnsByTable)
            : Collections.emptyMap();
    this.editableColumnsByTable =
        editableColumnsByTable != null
            ? Collections.unmodifiableMap(editableColumnsByTable)
            : Collections.emptyMap();
  }

  /**
   * 创建一个空的列权限信息载体。
   *
   * @return 空的列权限信息实例
   */
  public static ColumnScopeInfo empty() {
    return new ColumnScopeInfo(Collections.emptyMap(), Collections.emptyMap());
  }

  /**
   * 获取指定表的可见字段集合。
   *
   * @param tableName 表名（不区分大小写）
   * @return 可见字段集合，不存在时返回空集合
   */
  public Set<String> getVisibleColumns(String tableName) {
    if (tableName == null || visibleColumnsByTable.isEmpty()) {
      return Collections.emptySet();
    }
    return visibleColumnsByTable.getOrDefault(tableName.toLowerCase(), Collections.emptySet());
  }

  /**
   * 获取指定表的可编辑字段集合。
   *
   * @param tableName 表名（不区分大小写）
   * @return 可编辑字段集合，不存在时返回空集合
   */
  public Set<String> getEditableColumns(String tableName) {
    if (tableName == null || editableColumnsByTable.isEmpty()) {
      return Collections.emptySet();
    }
    return editableColumnsByTable.getOrDefault(tableName.toLowerCase(), Collections.emptySet());
  }

  /**
   * 判断指定表是否有可见字段。
   *
   * @param tableName 表名（不区分大小写）
   * @return 有可见字段时返回 {@code true}
   */
  public boolean hasVisibleColumns(String tableName) {
    return !getVisibleColumns(tableName).isEmpty();
  }

  /**
   * 判断指定表是否有可编辑字段。
   *
   * @param tableName 表名（不区分大小写）
   * @return 有可编辑字段时返回 {@code true}
   */
  public boolean hasEditableColumns(String tableName) {
    return !getEditableColumns(tableName).isEmpty();
  }

  /**
   * 判断列权限信息是否为空（无任何可见或可编辑字段）。
   *
   * @return 为空时返回 {@code true}
   */
  public boolean isEmpty() {
    return visibleColumnsByTable.isEmpty() && editableColumnsByTable.isEmpty();
  }
}
