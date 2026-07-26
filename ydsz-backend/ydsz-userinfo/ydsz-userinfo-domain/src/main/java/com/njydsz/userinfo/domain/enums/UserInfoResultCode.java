package com.njydsz.userinfo.domain.enums;

import com.njydsz.common.core.response.ResultCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户信息中心模块结果码枚举。
 *
 * <p>编码区间 B3xxxx，遵循 PMIS 统一错误码规范。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum UserInfoResultCode implements ResultCode {

    // ==================== B30xxx 用户/认证 ====================
    USER_NOT_FOUND("B30001", "用户不存在"),
    PASSWORD_INCORRECT("B30002", "密码错误"),
    USER_DISABLED("B30003", "用户已停用"),
    USER_LOCKED("B30004", "用户已被锁定"),
    USERNAME_DUPLICATE("B30005", "用户名已存在"),
    ACCOUNT_LOCKED("A20110", "账号已锁定，请稍后再试"),
    ACCOUNT_LOCKED_PERMANENT("B30006", "账号已被永久锁定，请联系管理员"),
    CAPTCHA_INVALID("B30007", "验证码无效或已过期"),
    CAPTCHA_REQUIRED("B30008", "请输入验证码"),
    MFA_REQUIRED("A20108", "需要双因素认证"),
    MFA_INVALID("A20109", "双因素认证码无效"),
    MFA_NOT_BOUND("B30009", "用户未绑定双因素认证"),
    OLD_PASSWORD_INCORRECT("B30010", "原密码错误"),
    PASSWORD_SAME_AS_OLD("B30011", "新密码不能与旧密码相同"),
    PASSWORD_TOO_WEAK("B30012", "密码强度不足"),
    PASSWORD_REUSED("B30013", "不能使用最近使用过的密码"),
    SESSION_EXPIRED("B30014", "会话已过期"),
    SESSION_NOT_FOUND("B30015", "会话不存在"),
    TOKEN_INVALID("A20003", "Token 无效"),
    OAUTH2_CODE_INVALID("B30016", "授权码无效或已过期"),
    OAUTH2_CLIENT_INVALID("B30017", "客户端 ID 无效"),
    OAUTH2_REDIRECT_URI_MISMATCH("B30018", "回调地址不匹配"),

    // ==================== B31xxx 组织架构 ====================
    DEPARTMENT_NOT_FOUND("B30101", "部门不存在"),
    DEPARTMENT_HAS_CHILDREN("B30102", "该部门下存在子部门，无法删除"),
    DEPARTMENT_HAS_USERS("B30103", "该部门下存在人员，无法删除"),
    DEPARTMENT_CODE_DUPLICATE("B30104", "部门编码已存在"),
    COMPANY_NOT_FOUND("B30105", "公司不存在"),
    COMPANY_CODE_DUPLICATE("B30106", "公司编码已存在"),

    // ==================== B32xxx RBAC ====================
    ROLE_NOT_FOUND("B32001", "角色不存在"),
    ROLE_CODE_DUPLICATE("B32002", "角色编码已存在"),
    ROLE_BUILTIN_CANNOT_DELETE("B32003", "内置角色不允许删除"),
    ROLE_HAS_USERS("B32004", "该角色下存在用户，无法删除"),
    PERMISSION_NOT_FOUND("B32005", "权限不存在"),
    MENU_NOT_FOUND("B32006", "菜单不存在"),
    MENU_HAS_CHILDREN("B32011", "该菜单下存在子菜单，无法删除"),
    POST_NOT_FOUND("B32007", "岗位不存在"),
    POST_CODE_DUPLICATE("B32008", "岗位编码已存在"),
    LANGUAGE_NOT_FOUND("B32009", "语言不存在"),
    LANGUAGE_CODE_DUPLICATE("B32010", "语言编码已存在");

    private final String code;
    private final String msg;
}
