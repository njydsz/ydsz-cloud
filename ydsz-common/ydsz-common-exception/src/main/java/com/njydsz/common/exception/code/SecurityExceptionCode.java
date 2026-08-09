package com.njydsz.common.exception.code;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;
import com.njydsz.common.exception.registry.YdszResultCode;

import lombok.Getter;

/**
 * 安全模块异常码。
 *
 * <p>认证异常（A02xxx）、权限异常（A03xxx）和安全异常（C01xxx）。
 * 覆盖身份认证、会话管理、权限校验、Token 安全、CSRF 防护等安全场景。
 *
 * @author ydsz-team
 * @since 2.0.0
 * @see CoreExceptionCode
 * @see RateLimitExceptionCode
 */
@Getter
@YdszResultCode(module = "security", description = "安全模块认证授权异常码")
public enum SecurityExceptionCode implements ExceptionCode {

    // ==================== A02 认证异常 ====================

    /** 未授权（原 ResponseCode.UNAUTHORIZED 100401） */
    UNAUTHORIZED("A02051", "unauthorized", 401),
    /** 未登录 */
    NOT_LOGGED_IN("A02052", "not.logged.in", 401),
    /** 会话过期 */
    SESSION_EXPIRED("A02053", "session.expired", 401),
    /** 认证失败 */
    AUTHENTICATION_FAILED("A02054", "authentication.failed", 401),
    /** 账号已禁用 */
    ACCOUNT_DISABLED("A02055", "account.disabled", 401),
    /** 账号在其他地方登录 */
    ACCOUNT_LOGGED_ELSEWHERE("A02056", "account.logged.elsewhere", 401),

    // ==================== A03 权限异常 ====================

    /** 禁止访问（原 ResponseCode.FORBIDDEN 100403） */
    FORBIDDEN("A03051", "forbidden", 403),
    /** 权限不足 */
    INSUFFICIENT_PERMISSIONS("A03052", "insufficient.permissions", 403),
    /** 访问被拒绝 */
    ACCESS_DENIED("A03053", "access.denied", 403),
    /** 角色不匹配 */
    ROLE_MISMATCH("A03054", "role.mismatch", 403),

    // ==================== C01 安全异常 ====================

    /** 安全访问被拒绝 */
    SEC_ACCESS_DENIED("C01051", "security.access.denied", 403),
    /** 需要认证 */
    AUTHENTICATION_REQUIRED("C01052", "security.authentication.required", 401),
    /** Token过期 */
    TOKEN_EXPIRED("C01053", "security.token.expired", 401),
    /** 权限拒绝 */
    PERMISSION_DENIED("C01054", "security.permission.denied", 403);

    // ==================== 字段定义 ====================

    /** 异常错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    SecurityExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    // ============================================================
    // 静态注册 & 便捷查找
    // ============================================================

    static {
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (SecurityExceptionCode code : values()) {
            registryMap.put(code.getCode(), code);
        }
        ExceptionCodeRegistry.register(registryMap);
    }

    /**
     * 便捷查找方法：按 code 字符串查找本模块的安全异常码枚举。
     *
     * @param code 异常码字符串
     * @return 对应的 SecurityExceptionCode 枚举实例；未找到或非 SecurityExceptionCode 返回 null
     */
    public static SecurityExceptionCode resolve(String code) {
        ExceptionCode ec = ExceptionCodeRegistry.lookup(code);
        return ec instanceof SecurityExceptionCode security ? security : null;
    }
}
