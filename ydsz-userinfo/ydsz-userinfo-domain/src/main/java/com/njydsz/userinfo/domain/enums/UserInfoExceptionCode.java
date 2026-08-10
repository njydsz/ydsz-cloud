package com.njydsz.userinfo.domain.enums;

import com.njydsz.common.exception.enums.ExceptionCode;

import lombok.Getter;
import com.njydsz.common.exception.registry.YdszResultCode;

/**
 * 用户信息中心模块异常码枚举。
 *
 * <p>P0-6: 已从旧版 {@code ResultCode} 体系迁移到 {@code common-exception} 的
 * {@link ExceptionCode} 体系，获得以下增强能力：
 * <ul>
 *   <li>统一异常码注册：通过 {@link ExceptionCodeRegistry} 全局注册，支持反查与文档生成</li>
 *   <li>HTTP 状态码：每个异常码携带精确的 HTTP 状态码（404/401/403/400）</li>
 *   <li>异常分类：由 {@link ExceptionCode#getCategory()} 按首字母推断（B → SYSTEM）</li>
 *   <li>i18n 支持：{@link #getKey()} 返回国际化消息键，由 {@code I18nConfiguration} 解析</li>
 * </ul>
 *
 * <p><b>编码区间</b>：
 * <ul>
 *   <li>B30xxx 用户/认证</li>
 *   <li>B31xxx 组织架构</li>
 *   <li>B32xxx RBAC（角色/权限/菜单/岗位/语言）</li>
 *   <li>A20xxx 安全认证（锁号/MFA/Token，HTTP 401）</li>
 * </ul>
 *
 * <p><b>稳定性</b>：错误码是业务契约，修改/废弃必须保留向前兼容。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszResultCode(module = "userinfo", description = "用户中心")
public enum UserInfoExceptionCode implements ExceptionCode {

    // ==================== B30xxx 用户/认证 ====================
    /** 用户不存在 */
    USER_NOT_FOUND("B30001", "userinfo.user.not.found", 404),
    /** 密码错误 */
    PASSWORD_INCORRECT("B30002", "userinfo.password.incorrect"),
    /** 用户已停用 */
    USER_DISABLED("B30003", "userinfo.user.disabled", 403),
    /** 用户已被锁定 */
    USER_LOCKED("B30004", "userinfo.user.locked", 403),
    /** 用户名已存在 */
    USERNAME_DUPLICATE("B30005", "userinfo.username.duplicate"),
    /** 账号已锁定，请稍后再试 */
    ACCOUNT_LOCKED("A20110", "userinfo.account.locked", 401),
    /** 账号已被永久锁定，请联系管理员 */
    ACCOUNT_LOCKED_PERMANENT("B30006", "userinfo.account.locked.permanent", 403),
    /** 验证码无效或已过期 */
    CAPTCHA_INVALID("B30007", "userinfo.captcha.invalid"),
    /** 请输入验证码 */
    CAPTCHA_REQUIRED("B30008", "userinfo.captcha.required"),
    /** 需要双因素认证 */
    MFA_REQUIRED("A20108", "userinfo.mfa.required", 401),
    /** 双因素认证码无效 */
    MFA_INVALID("A20109", "userinfo.mfa.invalid", 401),
    /** 用户未绑定双因素认证 */
    MFA_NOT_BOUND("B30009", "userinfo.mfa.not.bound"),
    /** 原密码错误 */
    OLD_PASSWORD_INCORRECT("B30010", "userinfo.password.old.incorrect"),
    /** 新密码不能与旧密码相同 */
    PASSWORD_SAME_AS_OLD("B30011", "userinfo.password.same.as.old"),
    /** 密码强度不足 */
    PASSWORD_TOO_WEAK("B30012", "userinfo.password.too.weak"),
    /** 不能使用最近使用过的密码 */
    PASSWORD_REUSED("B30013", "userinfo.password.reused"),
    /** IP 登录失败次数过多被临时封禁 */
    IP_BLOCKED("B30019", "userinfo.login.ip.blocked", 403),
    /** 会话已过期 */
    SESSION_EXPIRED("B30014", "userinfo.session.expired", 401),
    /** 会话不存在 */
    SESSION_NOT_FOUND("B30015", "userinfo.session.not.found", 401),
    /** Token 无效 */
    TOKEN_INVALID("A20003", "userinfo.token.invalid", 401),
    /** 授权码无效或已过期 */
    OAUTH2_CODE_INVALID("B30016", "userinfo.oauth2.code.invalid", 401),
    /** 客户端 ID 无效 */
    OAUTH2_CLIENT_INVALID("B30017", "userinfo.oauth2.client.invalid", 401),
    /** 回调地址不匹配 */
    OAUTH2_REDIRECT_URI_MISMATCH("B30018", "userinfo.oauth2.redirect.uri.mismatch", 401),

    // ==================== B31xxx 组织架构 ====================
    /** 部门不存在 */
    DEPARTMENT_NOT_FOUND("B30101", "userinfo.department.not.found", 404),
    /** 该部门下存在子部门，无法删除 */
    DEPARTMENT_HAS_CHILDREN("B30102", "userinfo.department.has.children"),
    /** 该部门下存在人员，无法删除 */
    DEPARTMENT_HAS_USERS("B30103", "userinfo.department.has.users"),
    /** 部门编码已存在 */
    DEPARTMENT_CODE_DUPLICATE("B30104", "userinfo.department.code.duplicate"),
    /** 公司不存在 */
    COMPANY_NOT_FOUND("B30105", "userinfo.company.not.found", 404),
    /** 公司编码已存在 */
    COMPANY_CODE_DUPLICATE("B30106", "userinfo.company.code.duplicate"),

    // ==================== B32xxx RBAC ====================
    /** 角色不存在 */
    ROLE_NOT_FOUND("B32001", "userinfo.role.not.found", 404),
    /** 角色编码已存在 */
    ROLE_CODE_DUPLICATE("B32002", "userinfo.role.code.duplicate"),
    /** 内置角色不允许删除 */
    ROLE_BUILTIN_CANNOT_DELETE("B32003", "userinfo.role.builtin.cannot.delete"),
    /** 该角色下存在用户，无法删除 */
    ROLE_HAS_USERS("B32004", "userinfo.role.has.users"),
    /** 权限不存在 */
    PERMISSION_NOT_FOUND("B32005", "userinfo.permission.not.found", 404),
    /** 菜单不存在 */
    MENU_NOT_FOUND("B32006", "userinfo.menu.not.found", 404),
    /** 该菜单下存在子菜单，无法删除 */
    MENU_HAS_CHILDREN("B32011", "userinfo.menu.has.children"),
    /** 岗位不存在 */
    POST_NOT_FOUND("B32007", "userinfo.post.not.found", 404),
    /** 岗位编码已存在 */
    POST_CODE_DUPLICATE("B32008", "userinfo.post.code.duplicate"),
    /** 语言不存在 */
    LANGUAGE_NOT_FOUND("B32009", "userinfo.language.not.found", 404),
    /** 语言编码已存在 */
    LANGUAGE_CODE_DUPLICATE("B32010", "userinfo.language.code.duplicate");

    /** 错误码（业务契约，不应轻易变更） */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    UserInfoExceptionCode(String code, String key) {
        this(code, key, 400);
    }

    UserInfoExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

}
