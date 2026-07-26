package com.njydsz.common.auth.model;

import java.io.Serializable;
import java.util.Map;

/**
 * RBAC 用户信息 DTO
 *
 * <p>类型安全的用户信息载体，替代原来的 Map<String, Object> 返回方式。
 * 包含用户基本信息、角色、权限数据等。</p>
 *
 * <p><b>安全特性：</b></p>
 * <ul>
 *   <li>敏感字段（密码等）不会包含在此 DTO 中</li>
 *   <li>扩展数据通过 typed extras 访问，避免类型转换错误</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class UserInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 部门ID
     */
    private String deptId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 扩展属性（用于承载自定义用户数据）
     */
    private transient Map<String, Object> extras;

    public UserInfo() {
    }

    public UserInfo(String userId, String username, String roleCode) {
        this.userId = userId;
        this.username = username;
        this.roleCode = roleCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    public void setExtras(Map<String, Object> extras) {
        this.extras = extras;
    }

    /**
     * 获取扩展属性（类型安全）
     *
     * @param key          属性键
     * @param targetType   目标类型
     * @param <T>          类型参数
     * @return 属性值，类型不匹配时返回 null
     */
    
    public <T> T getExtra(String key, Class<T> targetType) {
        if (extras == null || !extras.containsKey(key)) {
            return null;
        }
        Object value = extras.get(key);
        if (targetType.isInstance(value)) {
            return targetType.cast(value);
        }
        return null;
    }

    /**
     * 验证用户信息是否有效
     *
     * @return true 如果 userId、username、roleCode 均不为空
     */
    public boolean isValid() {
        return userId != null && !userId.isEmpty()
                && username != null && !username.isEmpty()
                && roleCode != null && !roleCode.isEmpty();
    }

    @Override
    public String toString() {
        return "UserInfo{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", roleCode='" + roleCode + '\'' +
                ", roleName='" + roleName + '\'' +
                ", deptId='" + deptId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                '}';
    }
}
