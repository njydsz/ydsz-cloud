package com.njydsz.userinfo.server.auth;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.util.string.StringUtils;
import com.njydsz.userinfo.domain.entity.Role;
import com.njydsz.userinfo.domain.entity.UserRole;
import com.njydsz.userinfo.infra.mapper.RoleMapper;
import com.njydsz.userinfo.infra.mapper.UserRoleMapper;

/**
 * 数据权限范围服务（按角色配置 dataScope）。
 *
 * <p>基于 Role 实体的 {@code dataScope} 字段（ALL/DEPT_AND_CHILD/DEPT/SELF/CUSTOM）， 提供按用户角色集合动态生成数据范围 WHERE
 * 条件的能力。
 *
 * <p><b>数据权限优先级（从宽到严）：</b>
 *
 * <ol>
 *   <li>{@code ALL}：全部数据，不加限制
 *   <li>{@code DEPT_AND_CHILD}：本部门及子部门数据
 *   <li>{@code DEPT}：仅本部门数据
 *   <li>{@code SELF}：仅本人数据
 *   <li>{@code CUSTOM}：自定义部门（需配合扩展部门表）
 * </ol>
 *
 * <p><b>多角色合并策略：</b>取并集（最宽松原则）。若任一角色为 ALL，则结果为 ALL。
 *
 * <p><b>TODO：</b>与 {@code AuthRowPermissionAspect} / {@code DataPermissionResolver} 集成，
 * 将本服务的解析结果注入到数据权限上下文中，替换当前基于 Redis role-row-key 的固定模式。 当前作为 Service 层接口提供，供业务模块按需调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataScopeService {

  /** 数据权限范围：全部数据 */
  public static final String SCOPE_ALL = "ALL";

  /** 数据权限范围：本部门及子部门 */
  public static final String SCOPE_DEPT_AND_CHILD = "DEPT_AND_CHILD";

  /** 数据权限范围：仅本部门 */
  public static final String SCOPE_DEPT = "DEPT";

  /** 数据权限范围：仅本人 */
  public static final String SCOPE_SELF = "SELF";

  /** 数据权限范围：自定义部门 */
  public static final String SCOPE_CUSTOM = "CUSTOM";

  /** 数据权限优先级数值（数值越大范围越宽） */
  private static final int PRIORITY_SELF = 1;

  private static final int PRIORITY_DEPT = 2;
  private static final int PRIORITY_DEPT_AND_CHILD = 3;
  private static final int PRIORITY_ALL = 99;

  private final RoleMapper roleMapper;
  private final UserRoleMapper userRoleMapper;

  /**
   * 解析当前用户的数据权限范围（取所有角色中最宽松的）。
   *
   * <p>遍历用户的所有角色，读取每个角色的 {@code dataScope} 字段， 按优先级取最宽松的范围。若用户无任何角色则返回 SELF（最严格）。
   *
   * @param userId 用户 ID
   * @return 合并后的数据权限范围枚举值，不会返回 null（最低返回 SELF）
   */
  public String resolveEffectiveScope(String userId) {
    if (StringUtils.isBlank(userId)) {
      return SCOPE_SELF;
    }
    List<Role> roles = loadUserRoles(userId);
    if (roles.isEmpty()) {
      log.debug("User {} has no roles, defaulting to SELF", userId);
      return SCOPE_SELF;
    }
    int maxPriority = 0;
    for (Role role : roles) {
      String scope = role.getDataScope();
      if (StringUtils.isBlank(scope)) {
        continue;
      }
      int priority = priorityOf(scope);
      if (priority > maxPriority) {
        maxPriority = priority;
        if (maxPriority >= PRIORITY_ALL) {
          break;
        }
      }
    }
    return scopeOfPriority(maxPriority);
  }

  /**
   * 生成数据权限 WHERE 条件描述（供 Service 层动态拼接 SQL）。
   *
   * <p>返回结构化描述，调用方根据 scope 值决定如何拼接 WHERE 条件。
   *
   * <ul>
   *   <li>ALL → 返回空列表（无需过滤）
   *   <li>DEPT / DEPT_AND_CHILD → 返回用户所在部门 ID 列表（含子部门）
   *   <li>SELF → 返回空列表，调用方应拼接 {@code user_id = ?}
   * </ul>
   *
   * @param userId 用户 ID
   * @param deptIds 用户所属部门 ID 列表（用于 DEPT 场景）
   * @return 数据权限描述对象
   */
  public DataScopeCondition buildScopeCondition(String userId, Set<String> deptIds) {
    String scope = resolveEffectiveScope(userId);
    DataScopeCondition condition = new DataScopeCondition();
    condition.setScope(scope);
    condition.setUserId(userId);
    if (SCOPE_ALL.equals(scope)) {
      // 全部数据，无需额外条件
      condition.setDeptIds(Collections.emptySet());
      return condition;
    }
    if (SCOPE_SELF.equals(scope)) {
      condition.setDeptIds(Collections.emptySet());
      return condition;
    }
    if (SCOPE_DEPT.equals(scope) || SCOPE_DEPT_AND_CHILD.equals(scope)) {
      condition.setDeptIds(deptIds != null ? deptIds : Collections.emptySet());
      return condition;
    }
    // CUSTOM 暂返回空，TODO: 通过自定义部门表查询
    condition.setDeptIds(Collections.emptySet());
    return condition;
  }

  /**
   * 按 userId 查询用户拥有的全部角色（仅启用状态）。
   *
   * @param userId 用户 ID
   * @return 用户持有的有效角色列表，无角色时返回空列表
   */
  private List<Role> loadUserRoles(String userId) {
    LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
    urWrapper.eq(UserRole::getUserId, userId);
    List<UserRole> userRoles = userRoleMapper.selectList(urWrapper);
    if (userRoles.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> roleIds = userRoles.stream()
        .map(UserRole::getRoleId)
        .distinct()
        .collect(Collectors.toList());
    LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
    roleWrapper.in(Role::getId, roleIds);
    roleWrapper.eq(Role::getStatus, "ENABLED");
    return roleMapper.selectList(roleWrapper);
  }

  /**
   * 获取数据权限范围的优先级数值。
   *
   * @param scope 数据范围枚举字符串（如 {@code ALL}、{@code DEPT}）
   * @return 优先级数值，值越大权限范围越宽
   */
  private int priorityOf(String scope) {
    if (scope == null) {
      return 0;
    }
    switch (scope.trim().toUpperCase()) {
      case SCOPE_SELF:
        return PRIORITY_SELF;
      case SCOPE_DEPT:
        return PRIORITY_DEPT;
      case SCOPE_DEPT_AND_CHILD:
        return PRIORITY_DEPT_AND_CHILD;
      case SCOPE_ALL:
        return PRIORITY_ALL;
      case SCOPE_CUSTOM:
        return PRIORITY_DEPT;
      default:
        log.warn("Unknown dataScope value: {}, treating as SELF", scope);
        return PRIORITY_SELF;
    }
  }

  /**
   * 根据优先级数值还原范围枚举值。
   *
   * @param priority 优先级数值
   * @return 对应的范围枚举字符串，未知优先级返回 {@code SELF}
   */
  private String scopeOfPriority(int priority) {
    switch (priority) {
      case PRIORITY_SELF:
        return SCOPE_SELF;
      case PRIORITY_DEPT:
        return SCOPE_DEPT;
      case PRIORITY_DEPT_AND_CHILD:
        return SCOPE_DEPT_AND_CHILD;
      case PRIORITY_ALL:
        return SCOPE_ALL;
      default:
        return SCOPE_SELF;
    }
  }

  /**
   * 数据权限条件描述（Service 层内部使用）。
   *
   * <p>封装数据权限的范围类型与关联部门 ID 集合，供 Service 层动态生成 WHERE 条件。
   */
  public static class DataScopeCondition {

    /** 数据权限范围类型（ALL/DEPT_AND_CHILD/DEPT/SELF/CUSTOM） */
    private String scope;

    /** 当前用户 ID（SELF 场景使用） */
    private String userId;

    /** 可见部门 ID 集合（DEPT/DEPT_AND_CHILD 场景使用） */
    private Set<String> deptIds;

    public String getScope() {
      return scope;
    }

    public void setScope(String scope) {
      this.scope = scope;
    }

    public String getUserId() {
      return userId;
    }

    public void setUserId(String userId) {
      this.userId = userId;
    }

    public Set<String> getDeptIds() {
      return deptIds;
    }

    public void setDeptIds(Set<String> deptIds) {
      this.deptIds = deptIds;
    }

    /**
     * 判断是否需要按部门过滤。
     *
     * @return 若需要按部门过滤返回 true
     */
    public boolean needsDeptFilter() {
      return deptIds != null
          && !deptIds.isEmpty()
          && (SCOPE_DEPT.equals(scope) || SCOPE_DEPT_AND_CHILD.equals(scope));
    }

    /**
     * 判断是否需要按用户过滤（SELF 场景）。
     *
     * @return 若仅本人数据返回 true
     */
    public boolean needsUserFilter() {
      return SCOPE_SELF.equals(scope);
    }

    /**
     * 判断是否无任何限制（ALL 场景）。
     *
     * @return 若全部可见返回 true
     */
    public boolean isAllScope() {
      return SCOPE_ALL.equals(scope);
    }
  }
}
