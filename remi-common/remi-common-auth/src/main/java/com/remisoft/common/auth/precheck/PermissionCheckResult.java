package com.remisoft.common.auth.precheck;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 权限预检结果。
 *
 * <p>封装权限预检的详细结果，包括：
 * <ul>
 *   <li>checkPassed：是否通过</li>
 *   <li>hasPermission：当前用户是否拥有权限</li>
 *   <li>missingPermissions：缺少的权限列表</li>
 *   <li>grantedPermissions：已有的权限列表</li>
 *   <li>message：预检消息</li>
 *   <li>suggestion：建议操作</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * PermissionCheckResult result = permissionPreChecker.checkApiPermissions(apiCodes);
 * if (!result.isCheckPassed()) {
 *     return Response.error(result.getCode(), result.getMessage());
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionCheckResult {

    /**
     * 是否通过预检
     */
    private boolean checkPassed;

    /**
     * 当前用户是否拥有所需权限
     */
    private boolean hasPermission;

    /**
     * 缺少的权限列表
     */
    private Set<String> missingPermissions;

    /**
     * 已有的权限列表
     */
    private Set<String> grantedPermissions;

    /**
     * 预检消息
     */
    private String message;

    /**
     * 建议操作
     */
    private String suggestion;

    /**
     * 错误码
     */
    private String errorCode;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 用户角色列表
     */
    private Set<String> userRoles;

    /**
     * 创建预检通过的结果（默认消息）。
     *
     * @return 预检通过结果
     */
    public static PermissionCheckResult pass() {
        return PermissionCheckResult.builder()
                .checkPassed(true)
                .hasPermission(true)
                .message("权限校验通过")
                .build();
    }

    /**
     * 创建预检通过的结果（自定义消息）。
     *
     * @param message 通过消息
     * @return 预检通过结果
     */
    public static PermissionCheckResult pass(String message) {
        return PermissionCheckResult.builder()
                .checkPassed(true)
                .hasPermission(true)
                .message(message)
                .build();
    }

    /**
     * 创建预检拒绝的结果（默认消息）。
     *
     * @param missingPermissions 缺少的权限集合
     * @param grantedPermissions 已有的权限集合
     * @return 预检拒绝结果
     */
    public static PermissionCheckResult deny(Set<String> missingPermissions, Set<String> grantedPermissions) {
        return PermissionCheckResult.builder()
                .checkPassed(false)
                .hasPermission(false)
                .missingPermissions(missingPermissions)
                .grantedPermissions(grantedPermissions)
                .message("权限不足")
                .errorCode("A03000")
                .build();
    }

    /**
     * 创建预检拒绝的结果（自定义消息，包含缺少和已有权限）。
     *
     * @param message 拒绝消息
     * @param missingPermissions 缺少的权限集合
     * @param grantedPermissions 已有的权限集合
     * @return 预检拒绝结果
     */
    public static PermissionCheckResult deny(String message, Set<String> missingPermissions, Set<String> grantedPermissions) {
        return PermissionCheckResult.builder()
                .checkPassed(false)
                .hasPermission(false)
                .missingPermissions(missingPermissions)
                .grantedPermissions(grantedPermissions)
                .message(message)
                .errorCode("A03000")
                .build();
    }

    /**
     * 创建预检拒绝的结果（自定义消息，仅包含缺少权限）。
     *
     * @param message 拒绝消息
     * @param missingPermissions 缺少的权限集合
     * @return 预检拒绝结果
     */
    public static PermissionCheckResult deny(String message, Set<String> missingPermissions) {
        return PermissionCheckResult.builder()
                .checkPassed(false)
                .hasPermission(false)
                .missingPermissions(missingPermissions)
                .message(message)
                .errorCode("A03000")
                .build();
    }

    public boolean isCheckPassed() {
        return checkPassed;
    }

    /**
     * 判断当前用户是否拥有所需权限。
     *
     * <p>与 {@link #isCheckPassed()} 的区分：本标志表示权限预检的最终结论，
     * 仅当 {@link #isCheckPassed()} 与本方法均返回 {@code true} 时请求才可放行。
     *
     * @return {@code true} 表示拥有所需权限，否则为 {@code false}
     */
    public boolean hasPermission() {
        return hasPermission;
    }
}