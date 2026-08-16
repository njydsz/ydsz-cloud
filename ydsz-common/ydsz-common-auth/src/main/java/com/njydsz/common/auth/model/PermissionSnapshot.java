package com.njydsz.common.auth.model;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 权限快照 —— 请求级别不可变权限缓存。
 *
 * <p>在请求开始时一次性从 Redis 加载所有权限信息，封装为不可变对象， 避免后续在鉴权流程中多次查询 Redis。该类是线程安全的（所有字段均为 final
 * 且不可变），可在多线程环境下安全共享。
 *
 * <p><b>包含的权限信息：</b>
 *
 * <ul>
 *   <li>用户 ID 与租户 ID
 *   <li>用户拥有的角色编码集合
 *   <li>菜单权限码集合（合并自所有角色）
 *   <li>按钮权限码集合（合并自所有角色）
 *   <li>接口权限码集合（合并自所有角色）
 * </ul>
 *
 * <p><b>构造方式：</b>使用 {@link Builder} 或 {@link #of} 工厂方法创建。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RolePermissions
 * @see Builder
 */
public final class PermissionSnapshot {

  private final String userId;

  private final String tenantId;

  private final Set<String> userRoles;

  private final Set<String> menuPermissions;

  private final Set<String> buttonPermissions;

  private final Set<String> apiPermissions;

  /** 私有构造方法，通过 Builder 或工厂方法调用。 */
  private PermissionSnapshot(
      String userId,
      String tenantId,
      Set<String> userRoles,
      Set<String> menuPermissions,
      Set<String> buttonPermissions,
      Set<String> apiPermissions) {
    this.userId = userId;
    this.tenantId = tenantId;
    this.userRoles =
        userRoles != null
            ? Collections.unmodifiableSet(new HashSet<>(userRoles))
            : Collections.emptySet();
    this.menuPermissions =
        menuPermissions != null
            ? Collections.unmodifiableSet(new HashSet<>(menuPermissions))
            : Collections.emptySet();
    this.buttonPermissions =
        buttonPermissions != null
            ? Collections.unmodifiableSet(new HashSet<>(buttonPermissions))
            : Collections.emptySet();
    this.apiPermissions =
        apiPermissions != null
            ? Collections.unmodifiableSet(new HashSet<>(apiPermissions))
            : Collections.emptySet();
  }

  /**
   * 静态工厂方法：将用户角色列表与多个 RolePermissions 合并为权限快照。
   *
   * <p>该方法将所有 {@link RolePermissions} 中的权限集合合并到同一个 快照中，产生去重后的全量权限视图。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @param userRoles 用户拥有的角色编码集合
   * @param rolePermissions 一个或多个角色的权限聚合，可为空
   * @return 权限快照实例
   */
  public static PermissionSnapshot of(
      String userId, String tenantId, Set<String> userRoles, RolePermissions... rolePermissions) {
    Set<String> menuSet = new HashSet<>();
    Set<String> buttonSet = new HashSet<>();
    Set<String> apiSet = new HashSet<>();

    if (rolePermissions != null) {
      for (RolePermissions rp : rolePermissions) {
        if (rp != null) {
          if (rp.getMenuPermissions() != null) {
            menuSet.addAll(rp.getMenuPermissions());
          }
          if (rp.getButtonPermissions() != null) {
            buttonSet.addAll(rp.getButtonPermissions());
          }
          if (rp.getApiPermissions() != null) {
            apiSet.addAll(rp.getApiPermissions());
          }
        }
      }
    }

    return new PermissionSnapshot(userId, tenantId, userRoles, menuSet, buttonSet, apiSet);
  }

  /**
   * 获取用户 ID。
   *
   * @return 用户 ID
   */
  public String getUserId() {
    return userId;
  }

  /**
   * 获取租户 ID。
   *
   * @return 租户 ID
   */
  public String getTenantId() {
    return tenantId;
  }

  /**
   * 获取用户角色编码集合。
   *
   * @return 不可变的角色编码集合
   */
  public Set<String> getUserRoles() {
    return userRoles;
  }

  /**
   * 获取菜单权限码集合。
   *
   * @return 不可变的菜单权限码集合
   */
  public Set<String> getMenuPermissions() {
    return menuPermissions;
  }

  /**
   * 获取按钮权限码集合。
   *
   * @return 不可变的按钮权限码集合
   */
  public Set<String> getButtonPermissions() {
    return buttonPermissions;
  }

  /**
   * 获取接口权限码集合。
   *
   * @return 不可变的接口权限码集合
   */
  public Set<String> getApiPermissions() {
    return apiPermissions;
  }

  /**
   * 判断是否拥有指定的菜单权限。
   *
   * @param code 菜单权限码
   * @return 拥有该权限时返回 {@code true}
   */
  public boolean hasMenuPermission(String code) {
    if (code == null || menuPermissions.isEmpty()) {
      return false;
    }
    return menuPermissions.contains(code.trim());
  }

  /**
   * 判断是否拥有指定的按钮权限。
   *
   * @param code 按钮权限码
   * @return 拥有该权限时返回 {@code true}
   */
  public boolean hasButtonPermission(String code) {
    if (code == null || buttonPermissions.isEmpty()) {
      return false;
    }
    return buttonPermissions.contains(code.trim());
  }

  /**
   * 判断是否拥有指定的接口权限。
   *
   * @param code 接口权限码
   * @return 拥有该权限时返回 {@code true}
   */
  public boolean hasApiPermission(String code) {
    if (code == null || apiPermissions.isEmpty()) {
      return false;
    }
    return apiPermissions.contains(code.trim());
  }

  /**
   * 判断当前用户是否为超级管理员。
   *
   * <p>当用户拥有的角色编码集合与 {@code ignoreRoles} 列表存在交集时， 判定为超级管理员，即拥有所有权限，跳过鉴权检查。
   *
   * @param ignoreRoles 超级管理员角色编码列表，不可为 null
   * @return 用户是超级管理员时返回 {@code true}
   * @throws NullPointerException 如果 ignoreRoles 为 null
   */
  public boolean isSuperAdmin(List<String> ignoreRoles) {
    Objects.requireNonNull(ignoreRoles, "ignoreRoles must not be null");
    if (userRoles.isEmpty() || ignoreRoles.isEmpty()) {
      return false;
    }
    for (String role : ignoreRoles) {
      if (role != null && userRoles.contains(role)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PermissionSnapshot that = (PermissionSnapshot) o;
    return Objects.equals(userId, that.userId)
        && Objects.equals(tenantId, that.tenantId)
        && Objects.equals(userRoles, that.userRoles)
        && Objects.equals(menuPermissions, that.menuPermissions)
        && Objects.equals(buttonPermissions, that.buttonPermissions)
        && Objects.equals(apiPermissions, that.apiPermissions);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        userId, tenantId, userRoles, menuPermissions, buttonPermissions, apiPermissions);
  }

  @Override
  public String toString() {
    return "PermissionSnapshot{"
        + "userId='"
        + userId
        + '\''
        + ", tenantId='"
        + tenantId
        + '\''
        + ", userRoles="
        + userRoles
        + ", menuPermissions="
        + menuPermissions
        + ", buttonPermissions="
        + buttonPermissions
        + ", apiPermissions="
        + apiPermissions
        + '}';
  }

  /**
   * 创建 Builder 实例。
   *
   * @return 新的 Builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * PermissionSnapshot 的构建器。
   *
   * <p>因字段较多，通过 Builder 模式逐步设置各项，再调用 {@link #build()} 创建不可变快照。
   */
  public static final class Builder {

    private String userId;

    private String tenantId;

    private Set<String> userRoles;

    private Set<String> menuPermissions;

    private Set<String> buttonPermissions;

    private Set<String> apiPermissions;

    private Builder() {}

    /**
     * 设置用户 ID。
     *
     * @param userId 用户 ID
     * @return this
     */
    public Builder userId(String userId) {
      this.userId = userId;
      return this;
    }

    /**
     * 设置租户 ID。
     *
     * @param tenantId 租户 ID
     * @return this
     */
    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    /**
     * 设置用户角色编码集合。
     *
     * @param userRoles 角色编码集合
     * @return this
     */
    public Builder userRoles(Set<String> userRoles) {
      this.userRoles = userRoles;
      return this;
    }

    /**
     * 设置菜单权限码集合。
     *
     * @param menuPermissions 菜单权限码集合
     * @return this
     */
    public Builder menuPermissions(Set<String> menuPermissions) {
      this.menuPermissions = menuPermissions;
      return this;
    }

    /**
     * 设置按钮权限码集合。
     *
     * @param buttonPermissions 按钮权限码集合
     * @return this
     */
    public Builder buttonPermissions(Set<String> buttonPermissions) {
      this.buttonPermissions = buttonPermissions;
      return this;
    }

    /**
     * 设置接口权限码集合。
     *
     * @param apiPermissions 接口权限码集合
     * @return this
     */
    public Builder apiPermissions(Set<String> apiPermissions) {
      this.apiPermissions = apiPermissions;
      return this;
    }

    /**
     * 构建 PermissionSnapshot 实例。
     *
     * @return 不可变的权限快照
     */
    public PermissionSnapshot build() {
      return new PermissionSnapshot(
          userId, tenantId, userRoles, menuPermissions, buttonPermissions, apiPermissions);
    }
  }
}
