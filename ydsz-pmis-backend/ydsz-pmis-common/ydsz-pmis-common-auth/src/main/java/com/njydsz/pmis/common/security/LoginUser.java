package com.njydsz.pmis.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录用户上下文
 *
 * <p>从 JWT Token 解析后存放于 ThreadLocal，供业务层使用。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID（雪花算法字符串） */
    private String userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 部门 ID（雪花算法字符串） */
    private String deptId;

    /** 部门名称 */
    private String deptName;

    /** 租户 ID（多租户上下文，默认 "1"） */
    private String tenantId;

    /** 职级编码 */
    private String levelCode;

    /** 角色编码列表 */
    private List<String> roles;

    /** 权限编码列表 (例: system:user:create) */
    private List<String> permissions;

    /** 数据权限范围: ALL/DEPT/SELF/CUSTOM */
    private String dataScope;

    /** 自定义部门 ID 集（CUSTOM 模式，雪花字符串） */
    private List<String> customDeptIds;

    /** 本部门及下级部门 ID 链（DEPT_AND_CHILD 模式，登录时计算并放入 JWT） */
    private List<String> deptIds;

    /** Token */
    private String token;

    /** 登录时间 */
    private Long loginTime;

    /** Token 过期时间（毫秒） */
    private Long expireTime;

    /**
     * 是否超级管理员
     *
     * @return true 表示拥有全部权限通配符
     */
    public boolean isSuperAdmin() {
        return permissions != null && permissions.contains("*:*:*");
    }

    /**
     * 是否拥有指定权限
     *
     * @param perm 权限编码
     * @return true 表示拥有该权限
     */
    public boolean hasPermission(String perm) {
        if (permissions == null) return false;
        if (isSuperAdmin()) return true;
        return permissions.contains(perm);
    }
}
