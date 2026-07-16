package com.njydsz.userinfo.web.constant;

/**
 * 权限码常量（userinfo-web 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-common-permission 包，因 common 重构后该常量类已迁移到各业务模块本地化。
 * 集中管理 userinfo-web 控制层使用的权限码字符串，避免在 {@code @AuthApiPermission} 注解中散落字面量。
 *
 * <p>使用规范：
 * <ul>
 *   <li>按业务域分组（USER / ROLE / DEPT / DICT / EMPLOYEE_TAG / 2FA ...）</li>
 *   <li>所有权限码以 {@code "userinfo:"} 前缀，避免与其他模块冲突</li>
 *   <li>对应 {@code @AuthApiPermission(apiCodes = ...)} 的 apiCodes 数组</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class PermissionCodes {

    /** 人员标签 — 创建 */
    public static final String RESOURCE_TAG_CREATE = "userinfo:resource_tag:create";
    /** 人员标签 — 删除 */
    public static final String RESOURCE_TAG_DELETE = "userinfo:resource_tag:delete";
    /** 人员标签 — 更新 */
    public static final String RESOURCE_TAG_UPDATE = "userinfo:resource_tag:update";

    private PermissionCodes() {
        throw new UnsupportedOperationException("Constants class");
    }
}
