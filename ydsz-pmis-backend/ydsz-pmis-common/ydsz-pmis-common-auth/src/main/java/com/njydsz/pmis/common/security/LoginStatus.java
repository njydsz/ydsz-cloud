package com.njydsz.pmis.common.security;

import lombok.Getter;

/**
 * 登录结果状态
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public enum LoginStatus {

    /** 登录成功 */
    SUCCESS("SUCCESS", "登录成功"),
    /** 密码错误 */
    FAIL_PASSWORD("FAIL_PASSWORD", "密码错误"),
    /** 用户不存在 */
    FAIL_USER_NOT_FOUND("FAIL_USER_NOT_FOUND", "用户不存在"),
    /** 账号已停用 */
    FAIL_DISABLED("FAIL_DISABLED", "账号已停用"),
    /** 账号已锁定 */
    FAIL_LOCKED("FAIL_LOCKED", "账号已锁定"),
    /** 账号已过期 */
    FAIL_EXPIRED("FAIL_EXPIRED", "账号已过期"),
    /** MFA 校验失败 */
    FAIL_MFA("FAIL_MFA", "MFA 校验失败"),
    /** 验证码错误 */
    FAIL_CAPTCHA("FAIL_CAPTCHA", "验证码错误"),
    /** 触发频率限制 */
    FAIL_RATE_LIMIT("FAIL_RATE_LIMIT", "触发频率限制"),
    /** 其他失败 */
    FAIL_OTHER("FAIL_OTHER", "其他失败");

    /** 状态编码 */
    private final String code;

    /** 状态描述 */
    private final String desc;

    LoginStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 是否登录成功
     *
     * @return true 表示登录成功
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
