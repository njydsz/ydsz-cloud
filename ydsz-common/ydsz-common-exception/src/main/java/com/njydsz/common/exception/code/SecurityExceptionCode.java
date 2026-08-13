package com.njydsz.common.exception.code;
import lombok.Getter;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.registry.YdszExceptionCode;

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
@YdszExceptionCode(module = "security", description = "安全模块认证授权异常码")
public enum SecurityExceptionCode implements ExceptionCode {

    // ==================== A02 认证异常 ====================

    /**
     * 未授权（原 ResponseCode.UNAUTHORIZED 100401）
     * @param "A02051" "A02051" 参数说明
     * @param "unauthorized" "unauthorized" 参数说明
     * @param NOT_LOGGED_IN("A02052" NOT_LOGGED_IN("A02052" 参数说明
     * @param "not.logged.in" "not.logged.in" 参数说明
     * @param SESSION_EXPIRED("A02053" SESSION_EXPIRED("A02053" 参数说明
     * @param "session.expired" "session.expired" 参数说明
     * @param AUTHENTICATION_FAILED("A02054" AUTHENTICATION_FAILED("A02054" 参数说明
     * @param "authentication.failed" "authentication.failed" 参数说明
     * @param ACCOUNT_DISABLED("A02055" ACCOUNT_DISABLED("A02055" 参数说明
     * @param "account.disabled" "account.disabled" 参数说明
     * @param ACCOUNT_LOGGED_ELSEWHERE("A02056" ACCOUNT_LOGGED_ELSEWHERE("A02056" 参数说明
     * @param "account.logged.elsewhere" "account.logged.elsewhere" 参数说明
     * @param FORBIDDEN("A03051" FORBIDDEN("A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     * @param INSUFFICIENT_PERMISSIONS("A03052" INSUFFICIENT_PERMISSIONS("A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A02051" "A02051" 参数说明
     * @param "unauthorized" "unauthorized" 参数说明
     */
    UNAUTHORIZED("A02051", "unauthorized", 401),
    /**
     * 未登录
     * @param "A02052" "A02052" 参数说明
     * @param "not.logged.in" "not.logged.in" 参数说明
     * @param SESSION_EXPIRED("A02053" SESSION_EXPIRED("A02053" 参数说明
     * @param "session.expired" "session.expired" 参数说明
     * @param AUTHENTICATION_FAILED("A02054" AUTHENTICATION_FAILED("A02054" 参数说明
     * @param "authentication.failed" "authentication.failed" 参数说明
     * @param ACCOUNT_DISABLED("A02055" ACCOUNT_DISABLED("A02055" 参数说明
     * @param "account.disabled" "account.disabled" 参数说明
     * @param ACCOUNT_LOGGED_ELSEWHERE("A02056" ACCOUNT_LOGGED_ELSEWHERE("A02056" 参数说明
     * @param "account.logged.elsewhere" "account.logged.elsewhere" 参数说明
     * @param FORBIDDEN("A03051" FORBIDDEN("A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     * @param INSUFFICIENT_PERMISSIONS("A03052" INSUFFICIENT_PERMISSIONS("A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A02052" "A02052" 参数说明
     * @param "not.logged.in" "not.logged.in" 参数说明
     */
    NOT_LOGGED_IN("A02052", "not.logged.in", 401),
    /**
     * 会话过期
     * @param "A02053" "A02053" 参数说明
     * @param "session.expired" "session.expired" 参数说明
     * @param AUTHENTICATION_FAILED("A02054" AUTHENTICATION_FAILED("A02054" 参数说明
     * @param "authentication.failed" "authentication.failed" 参数说明
     * @param ACCOUNT_DISABLED("A02055" ACCOUNT_DISABLED("A02055" 参数说明
     * @param "account.disabled" "account.disabled" 参数说明
     * @param ACCOUNT_LOGGED_ELSEWHERE("A02056" ACCOUNT_LOGGED_ELSEWHERE("A02056" 参数说明
     * @param "account.logged.elsewhere" "account.logged.elsewhere" 参数说明
     * @param FORBIDDEN("A03051" FORBIDDEN("A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     * @param INSUFFICIENT_PERMISSIONS("A03052" INSUFFICIENT_PERMISSIONS("A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A02053" "A02053" 参数说明
     * @param "session.expired" "session.expired" 参数说明
     */
    SESSION_EXPIRED("A02053", "session.expired", 401),
    /**
     * 认证失败
     * @param "A02054" "A02054" 参数说明
     * @param "authentication.failed" "authentication.failed" 参数说明
     * @param ACCOUNT_DISABLED("A02055" ACCOUNT_DISABLED("A02055" 参数说明
     * @param "account.disabled" "account.disabled" 参数说明
     * @param ACCOUNT_LOGGED_ELSEWHERE("A02056" ACCOUNT_LOGGED_ELSEWHERE("A02056" 参数说明
     * @param "account.logged.elsewhere" "account.logged.elsewhere" 参数说明
     * @param FORBIDDEN("A03051" FORBIDDEN("A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     * @param INSUFFICIENT_PERMISSIONS("A03052" INSUFFICIENT_PERMISSIONS("A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A02054" "A02054" 参数说明
     * @param "authentication.failed" "authentication.failed" 参数说明
     */
    AUTHENTICATION_FAILED("A02054", "authentication.failed", 401),
    /**
     * 账号已禁用
     * @param "A02055" "A02055" 参数说明
     * @param "account.disabled" "account.disabled" 参数说明
     * @param ACCOUNT_LOGGED_ELSEWHERE("A02056" ACCOUNT_LOGGED_ELSEWHERE("A02056" 参数说明
     * @param "account.logged.elsewhere" "account.logged.elsewhere" 参数说明
     * @param FORBIDDEN("A03051" FORBIDDEN("A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     * @param INSUFFICIENT_PERMISSIONS("A03052" INSUFFICIENT_PERMISSIONS("A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A02055" "A02055" 参数说明
     * @param "account.disabled" "account.disabled" 参数说明
     */
    ACCOUNT_DISABLED("A02055", "account.disabled", 401),
    /**
     * 账号在其他地方登录
     * @param "A02056" "A02056" 参数说明
     * @param "account.logged.elsewhere" "account.logged.elsewhere" 参数说明
     * @param FORBIDDEN("A03051" FORBIDDEN("A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     * @param INSUFFICIENT_PERMISSIONS("A03052" INSUFFICIENT_PERMISSIONS("A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A02056" "A02056" 参数说明
     * @param "account.logged.elsewhere" "account.logged.elsewhere" 参数说明
     */
    ACCOUNT_LOGGED_ELSEWHERE("A02056", "account.logged.elsewhere", 401),

    // ==================== A03 权限异常 ====================

    /**
     * 禁止访问（原 ResponseCode.FORBIDDEN 100403）
     * @param "A03051" "A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     * @param INSUFFICIENT_PERMISSIONS("A03052" INSUFFICIENT_PERMISSIONS("A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A03051" "A03051" 参数说明
     * @param "forbidden" "forbidden" 参数说明
     */
    FORBIDDEN("A03051", "forbidden", 403),
    /**
     * 权限不足
     * @param "A03052" "A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     * @param ACCESS_DENIED("A03053" ACCESS_DENIED("A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A03052" "A03052" 参数说明
     * @param "insufficient.permissions" "insufficient.permissions" 参数说明
     */
    INSUFFICIENT_PERMISSIONS("A03052", "insufficient.permissions", 403),
    /**
     * 访问被拒绝
     * @param "A03053" "A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     * @param ROLE_MISMATCH("A03054" ROLE_MISMATCH("A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A03053" "A03053" 参数说明
     * @param "access.denied" "access.denied" 参数说明
     */
    ACCESS_DENIED("A03053", "access.denied", 403),
    /**
     * 角色不匹配
     * @param "A03054" "A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     * @param SEC_ACCESS_DENIED("C01051" SEC_ACCESS_DENIED("C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "A03054" "A03054" 参数说明
     * @param "role.mismatch" "role.mismatch" 参数说明
     */
    ROLE_MISMATCH("A03054", "role.mismatch", 403),

    // ==================== C01 安全异常 ====================

    /**
     * 安全访问被拒绝
     * @param "C01051" "C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     * @param AUTHENTICATION_REQUIRED("C01052" AUTHENTICATION_REQUIRED("C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "C01051" "C01051" 参数说明
     * @param "security.access.denied" "security.access.denied" 参数说明
     */
    SEC_ACCESS_DENIED("C01051", "security.access.denied", 403),
    /**
     * 需要认证
     * @param "C01052" "C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     * @param TOKEN_EXPIRED("C01053" TOKEN_EXPIRED("C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "C01052" "C01052" 参数说明
     * @param "security.authentication.required" "security.authentication.required" 参数说明
     */
    AUTHENTICATION_REQUIRED("C01052", "security.authentication.required", 401),
    /**
     * Token过期
     * @param "C01053" "C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "C01053" "C01053" 参数说明
     * @param "security.token.expired" "security.token.expired" 参数说明
     * @param PERMISSION_DENIED("C01054" PERMISSION_DENIED("C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     */
    TOKEN_EXPIRED("C01053", "security.token.expired", 401),
    /**
     * 权限拒绝
     * @param "C01054" "C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     * @param 403 403 参数说明
     * @return 处理结果
     * @param "C01054" "C01054" 参数说明
     * @param "security.permission.denied" "security.permission.denied" 参数说明
     */
    PERMISSION_DENIED("C01054", "security.permission.denied", 403);

    // ==================== 字段定义 ====================

    /**
     * 异常错误码
     */
    private final String code;
    /**
     * 国际化消息键
     */
    private final String key;
    /**
     * HTTP 状态码
     */
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
}
