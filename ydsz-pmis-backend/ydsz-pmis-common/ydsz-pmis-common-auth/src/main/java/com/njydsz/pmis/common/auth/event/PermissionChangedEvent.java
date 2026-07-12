package com.njydsz.pmis.common.auth.event;

import java.io.Serializable;
import java.util.Set;

/**
 * 权限变更事件。
 *
 * <p>当用户的角色/权限/菜单/数据权限/列权限发生变更时，发布此事件。
 * 监听器应负责使对应权限缓存失效。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>角色权限被修改后</li>
 *   <li>用户角色被分配或移除后</li>
 *   <li>数据范围配置变更后</li>
 *   <li>列权限配置变更后</li>
 *   <li>角色被删除后</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class PermissionChangedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限变更类型枚举
     */
    public enum PermissionChangeType {
        ROLE_PERMISSION_CHANGED,
        USER_ROLE_CHANGED,
        DATA_SCOPE_CHANGED,
        MENU_CHANGED,
        COLUMN_PERMISSION_CHANGED,
        ROLE_DATA_SCOPE_CHANGED,
        ROLE_COLUMN_PERMISSION_CHANGED,
        ROLE_DELETED,
        ALL
    }

    /**
     * 变更类型
     */
    private final PermissionChangeType changeType;

    /**
     * 角色编码
     */
    private final String roleCode;

    /**
     * 受影响的权限类型集合
     */
    private final transient Set<String> affectedPermissionTypes;

    /**
     * 来源节点标识
     */
    private final String sourceNode;

    private final long timestamp;

    public PermissionChangedEvent(String roleCode, PermissionChangeType changeType,
                                  Set<String> affectedPermissionTypes, String sourceNode) {
        this.roleCode = roleCode;
        this.changeType = changeType;
        this.affectedPermissionTypes = affectedPermissionTypes;
        this.sourceNode = sourceNode;
        this.timestamp = System.currentTimeMillis();
    }

    public static PermissionChangedEvent rolePermissionChanged(String roleId) {
        return new PermissionChangedEvent(roleId, PermissionChangeType.ROLE_PERMISSION_CHANGED, null, null);
    }

    public static PermissionChangedEvent userRoleChanged(String userId) {
        return new PermissionChangedEvent(userId, PermissionChangeType.USER_ROLE_CHANGED, null, null);
    }

    public static PermissionChangedEvent dataScopeChanged(String scopeId) {
        return new PermissionChangedEvent(scopeId, PermissionChangeType.DATA_SCOPE_CHANGED, null, null);
    }

    public static PermissionChangedEvent menuChanged() {
        return new PermissionChangedEvent("ALL", PermissionChangeType.MENU_CHANGED, null, null);
    }

    public static PermissionChangedEvent allChanged() {
        return new PermissionChangedEvent("ALL", PermissionChangeType.ALL, null, null);
    }

    /**
     * 获取变更类型
     */
    public PermissionChangeType getChangeType() {
        return changeType;
    }

    /**
     * 获取角色编码
     */
    public String getRoleCode() {
        return roleCode;
    }

    /**
     * 获取受影响的权限类型集合
     */
    public Set<String> getAffectedPermissionTypes() {
        return affectedPermissionTypes;
    }

    /**
     * 获取来源节点标识
     */
    public String getSourceNode() {
        return sourceNode;
    }

    /**
     * 获取事件时间戳
     */
    public long getTimestamp() {
        return timestamp;
    }
}
