package com.njydsz.pmis.system.server.security;

/**
 * 登录状态枚举（system 模块本地版本）
 *
 * <p>用于 {@link LoginAuditEvent#getStatus()}，统一登录链路的状态语义。
 * 原参考实现位于 common-security 包，因 common 重构后已迁移到各业务模块本地化。
 *
 * @since 1.0.0
 */
public enum LoginStatus {

    /** 登录成功 */
    SUCCESS,
    /** 用户名不存在 */
    FAIL_USER_NOT_FOUND,
    /** 密码错误（未触发锁定） */
    FAIL_PASSWORD,
    /** 账号已锁定 */
    FAIL_LOCKED,
    /** 账号已停用 */
    FAIL_DISABLED,
    /** 2FA 验证失败 */
    FAIL_MFA,
    /** 其它失败原因（未知异常、验证码错误等） */
    FAIL_OTHER
}
