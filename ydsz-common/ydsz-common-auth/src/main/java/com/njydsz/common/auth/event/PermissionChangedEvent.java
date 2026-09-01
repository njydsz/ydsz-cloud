package com.njydsz.common.auth.event;

import java.io.Serializable;
import java.util.Set;

import org.springframework.context.ApplicationEvent;

/**
 * 权限变更事件。
 *
 * <p>当用户的角色/权限/菜单/数据权限/列权限发生变更时，发布此事件。 监听器应负责使对应权限缓存失效。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>角色权限被修改后
 *   <li>用户角色被分配或移除后
 *   <li>数据范围配置变更后
 *   <li>列权限配置变更后
 *   <li>角色被删除后
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class PermissionChangedEvent extends ApplicationEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 权限变更类型枚举 */
  public enum PermissionChangeType {
    /** 角色权限变更 */
    ROLE_PERMISSION_CHANGED,
    /** 用户角色变更 */
    USER_ROLE_CHANGED,
    /** 数据范围变更 */
    DATA_SCOPE_CHANGED,
    /** 菜单变更 */
    MENU_CHANGED,
    /** 列权限变更 */
    COLUMN_PERMISSION_CHANGED,
    /** 角色数据范围变更 */
    ROLE_DATA_SCOPE_CHANGED,
    /** 角色列权限变更 */
    ROLE_COLUMN_PERMISSION_CHANGED,
    /** 角色删除 */
    ROLE_DELETED,
    /** 全部变更 */
    ALL
  }

  /** 变更类型 */
  private final PermissionChangeType changeType;

  /** 角色编码 */
  private final String roleCode;

  /** 受影响的权限类型集合 */
  private final transient Set<String> affectedPermissionTypes;

  /** 来源节点标识 */
  private final String sourceNode;

  private final long timestamp;

  public PermissionChangedEvent(
      String roleCode,
      PermissionChangeType changeType,
      Set<String> affectedPermissionTypes,
      String sourceNode) {
    super(sourceNode != null ? sourceNode : PermissionChangedEvent.class.getName());
    this.roleCode = roleCode;
    this.changeType = changeType;
    this.affectedPermissionTypes = affectedPermissionTypes;
    this.sourceNode = sourceNode;
    this.timestamp = System.currentTimeMillis();
  }

  /**
   * 创建"角色权限变更"事件。
   *
   * @param roleId 角色 ID，将写入 {@link #getRoleCode()} 作为缓存失效的键
   * @return 变更类型为 {@link PermissionChangeType#ROLE_PERMISSION_CHANGED} 的事件实例
   */
  public static PermissionChangedEvent rolePermissionChanged(String roleId) {
    return new PermissionChangedEvent(
        roleId, PermissionChangeType.ROLE_PERMISSION_CHANGED, null, null);
  }

  /**
   * 创建"用户角色变更"事件。
   *
   * @param userId 用户 ID，将写入 {@link #getRoleCode()} 作为缓存失效的键
   * @return 变更类型为 {@link PermissionChangeType#USER_ROLE_CHANGED} 的事件实例
   */
  public static PermissionChangedEvent userRoleChanged(String userId) {
    return new PermissionChangedEvent(userId, PermissionChangeType.USER_ROLE_CHANGED, null, null);
  }

  /**
   * 创建"数据范围变更"事件。
   *
   * @param scopeId 数据范围 ID，将写入 {@link #getRoleCode()} 作为缓存失效的键
   * @return 变更类型为 {@link PermissionChangeType#DATA_SCOPE_CHANGED} 的事件实例
   */
  public static PermissionChangedEvent dataScopeChanged(String scopeId) {
    return new PermissionChangedEvent(scopeId, PermissionChangeType.DATA_SCOPE_CHANGED, null, null);
  }

  /**
   * 创建"菜单变更"事件。
   *
   * <p>影响全部菜单缓存，角色编码固定为 {@code "ALL"}。
   *
   * @return 变更类型为 {@link PermissionChangeType#MENU_CHANGED} 的事件实例
   */
  public static PermissionChangedEvent menuChanged() {
    return new PermissionChangedEvent("ALL", PermissionChangeType.MENU_CHANGED, null, null);
  }

  /**
   * 创建"全部权限变更"事件。
   *
   * <p>用于通知所有节点清空全部权限缓存，角色编码固定为 {@code "ALL"}。
   *
   * @return 变更类型为 {@link PermissionChangeType#ALL} 的事件实例
   */
  public static PermissionChangedEvent allChanged() {
    return new PermissionChangedEvent("ALL", PermissionChangeType.ALL, null, null);
  }

  /**
   * 获取变更类型。
   *
   * @return 变更类型
   */
  public PermissionChangeType getChangeType() {
    return changeType;
  }

  /**
   * 获取角色编码。
   *
   * @return 角色编码
   */
  public String getRoleCode() {
    return roleCode;
  }

  /**
   * 获取受影响的权限类型集合。
   *
   * @return 权限类型集合（不可变）
   */
  public Set<String> getAffectedPermissionTypes() {
    return affectedPermissionTypes;
  }

  /**
   * 获取来源节点标识。
   *
   * @return 来源节点标识
   */
  public String getSourceNode() {
    return sourceNode;
  }

  /**
   * 获取事件时间戳。
   *
   * <p>注意：不能使用 {@code getTimestamp()} 方法名，因为 {@link ApplicationEvent} 已有同名的 final 方法。
   *
   * @return 事件时间戳（毫秒）
   */
  public long getEventTimestamp() {
    return timestamp;
  }
}
