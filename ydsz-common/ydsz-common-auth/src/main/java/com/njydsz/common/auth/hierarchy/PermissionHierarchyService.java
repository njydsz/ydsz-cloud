package com.njydsz.common.auth.hierarchy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.njydsz.common.auth.util.PermissionUtils;

/**
 * 权限继承层级管理器（Spring Bean）。
 *
 * <p>支持权限树继承：拥有父权限自动拥有子权限。 例如：拥有 {@code sys:user} 自动拥有 {@code sys:user:list}、{@code sys:user:add}
 * 等。
 *
 * <p>支持按租户隔离，每个租户拥有独立的权限层级结构。 使用 {@code Map<tenantId, Map>} 结构实现租户间数据隔离。
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} 保证并发安全。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class PermissionHierarchyService {

  /** 默认租户 ID，用于向后兼容全局单例模式。 静态 {@link PermissionHierarchy} 门面委托到此租户。 */
  public static final String DEFAULT_TENANT_ID = "__DEFAULT__";

  /** 租户 ID → 租户层级数据的映射。 每个租户拥有独立的 parentToChildren 和 childToParents 索引。 */
  private final Map<String, TenantHierarchy> tenantHierarchies = new ConcurrentHashMap<>();

  /**
   * 注册权限继承关系。
   *
   * <p>拥有 {@code code} 自动拥有 {@code impliedCodes}。 例如：{@code registerPermission("tenant1",
   * "sys:user", "sys:user:list", "sys:user:add")} 表示拥有 {@code sys:user} 自动拥有 {@code sys:user:list}
   * 和 {@code sys:user:add}。
   *
   * @param tenantId 租户 ID，不可为 null
   * @param code 父权限码
   * @param impliedCodes 隐含的子权限码
   */
  public void registerPermission(String tenantId, String code, String... impliedCodes) {
    if (tenantId == null
        || code == null
        || code.isBlank()
        || impliedCodes == null
        || impliedCodes.length == 0) {
      return;
    }
    String parent = code.trim();
    TenantHierarchy hierarchy =
        tenantHierarchies.computeIfAbsent(tenantId, k -> new TenantHierarchy());
    Set<String> childSet =
        hierarchy.parentToChildren.computeIfAbsent(parent, k -> ConcurrentHashMap.newKeySet());
    for (String implied : impliedCodes) {
      if (implied != null && !implied.isBlank()) {
        String child = implied.trim();
        childSet.add(child);
        hierarchy
            .childToParents
            .computeIfAbsent(child, k -> ConcurrentHashMap.newKeySet())
            .add(parent);
      }
    }
  }

  /**
   * 获取指定权限的所有隐含权限（递归）。
   *
   * <p>返回拥有 {@code code} 后自动拥有的所有子权限。 例如：{@code getImpliedPermissions("tenant1", "sys:user")} 返回
   * {@code ["sys:user:list", "sys:user:add", "sys:user:edit", ...]}。
   *
   * @param tenantId 租户 ID，不可为 null
   * @param code 父权限码
   * @return 所有隐含权限集合，不会返回 null
   */
  public Set<String> getImpliedPermissions(String tenantId, String code) {
    if (tenantId == null || code == null || code.isBlank()) {
      return Collections.emptySet();
    }
    TenantHierarchy hierarchy = tenantHierarchies.get(tenantId);
    if (hierarchy == null) {
      return Collections.emptySet();
    }
    Set<String> result = new HashSet<>(16);
    collectChildren(hierarchy, code.trim(), result, new HashSet<>(16));
    return Collections.unmodifiableSet(result);
  }

  /**
   * 判断用户是否拥有指定权限（考虑权限继承）。
   *
   * <p>如果用户直接拥有该权限，返回 true。 如果用户拥有该权限的某个父级权限，也返回 true（递归检查）。
   *
   * @param tenantId 租户 ID，不可为 null
   * @param granted 用户已授权的权限集合
   * @param required 需要校验的权限码
   * @param wildcardEnabled 是否启用通配符匹配
   * @return 拥有权限返回 true
   */
  public boolean hasPermission(
      String tenantId, Set<String> granted, String required, boolean wildcardEnabled) {
    if (tenantId == null
        || granted == null
        || granted.isEmpty()
        || required == null
        || required.isBlank()) {
      return false;
    }
    // 1. 直接匹配
    if (PermissionUtils.hasPermission(granted, required, wildcardEnabled)) {
      return true;
    }
    // 2. 检查是否拥有父级权限（O(1) Map 查找）
    TenantHierarchy hierarchy = tenantHierarchies.get(tenantId);
    if (hierarchy == null) {
      return false;
    }
    Set<String> parents = hierarchy.childToParents.get(required.trim());
    if (parents != null) {
      for (String parent : parents) {
        if (PermissionUtils.hasPermission(granted, parent, wildcardEnabled)) {
          return true;
        }
        // 递归检查父级权限的父级
        if (hasPermission(tenantId, granted, parent, wildcardEnabled)) {
          return true;
        }
      }
    }
    return false;
  }

  /** 清理所有租户的权限层级。 */
  public void clear() {
    tenantHierarchies.clear();
  }

  /**
   * 清理指定租户的权限层级。
   *
   * @param tenantId 租户 ID
   */
  public void clear(String tenantId) {
    if (tenantId != null) {
      tenantHierarchies.remove(tenantId);
    }
  }

  /**
   * 获取指定租户的已注册父权限数量。
   *
   * @param tenantId 租户 ID，不可为 null
   * @return 父权限数量
   */
  public int getRegisteredParentCount(String tenantId) {
    if (tenantId == null) {
      return 0;
    }
    TenantHierarchy hierarchy = tenantHierarchies.get(tenantId);
    return hierarchy != null ? hierarchy.parentToChildren.size() : 0;
  }

  /**
   * 递归收集所有子权限。
   *
   * @param hierarchy 租户层级数据
   * @param parent 父权限码
   * @param result 结果集合
   * @param visited 已访问集合（防循环）
   */
  private void collectChildren(
      TenantHierarchy hierarchy, String parent, Set<String> result, Set<String> visited) {
    if (visited.contains(parent)) {
      return;
    }
    visited.add(parent);
    Set<String> directChildren = hierarchy.parentToChildren.get(parent);
    if (directChildren != null) {
      for (String child : directChildren) {
        result.add(child);
        collectChildren(hierarchy, child, result, visited);
      }
    }
  }

  /**
   * 单个租户的权限层级数据。
   *
   * <p>包含父→子和子→父的双向索引，支持 O(1) 查找。
   */
  private static class TenantHierarchy {

    /** 父权限 → 子权限集合的映射 */
    private final Map<String, Set<String>> parentToChildren = new ConcurrentHashMap<>();

    /** 子权限 → 父权限集合的映射（反向索引） */
    private final Map<String, Set<String>> childToParents = new ConcurrentHashMap<>();
  }
}
