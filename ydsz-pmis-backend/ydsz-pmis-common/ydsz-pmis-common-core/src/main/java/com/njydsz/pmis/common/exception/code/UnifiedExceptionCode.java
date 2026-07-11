package com.njydsz.pmis.common.exception.code;

import com.njydsz.pmis.common.exception.enums.ExceptionCategory;
import com.njydsz.pmis.common.exception.enums.ExceptionLevel;
import lombok.Getter;

/**
 * 统一异常码枚举
 *
 * <p>整合项目原有的 {@code BizErrorCode}（int code）编码体系，
 * 采用字母+数字风格，语义更清晰。所有新增异常场景统一使用此枚举。
 *
 * <p><b>编码规范：</b>
 * <pre>
 *     [类型(1位)] + [模块(2位)] + [序号(3位)]
 * </pre>
 *
 * <p><b>类型定义：</b>
 * <ul>
 *   <li>A - 业务级错误（对应 HTTP 4xx）</li>
 *   <li>B - 系统级错误（对应 HTTP 5xx）</li>
 *   <li>C - 安全级错误（对应 HTTP 401/403）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Getter
public enum UnifiedExceptionCode implements ExceptionCode {

    // ==================== 成功 ====================

    /** 操作成功 */
    SUCCESS("A00000", "success", 200),

    // ==================== A01 参数/业务异常 ====================

    /** 操作失败 */
    FAIL("A01051", "operation.fail", 400),
    /** 参数错误 */
    PARAM_ERROR("A01052", "param.error", 400),
    /** 非法参数 */
    ILLEGAL_ARGUMENT("A01053", "illegal.argument", 400),
    /** 请求格式无效 */
    INVALID_REQUEST_FORMAT("A01054", "invalid.request.format", 400),
    /** 业务状态无效 */
    INVALID_BUSINESS_STATE("A01055", "invalid.business.state", 400),
    /** 业务规则违反 */
    BUSINESS_RULE_VIOLATION("A01056", "business.rule.violation", 400),
    /** 通用业务错误 */
    BUSINESS_ERROR("A01057", "business.error", 400),
    /** 请求方法不允许 */
    METHOD_NOT_ALLOWED("A01058", "method.not.allowed", 405),
    /** 重复提交 */
    DUPLICATE_SUBMISSION("A01059", "duplicate.submission", 400),
    /** 流程状态无效 */
    INVALID_FLOW_STATE("A01060", "invalid.flow.state", 400),
    /** 乐观锁冲突/并发冲突 */
    OPTIMISTIC_LOCK_CONFLICT("A01061", "optimistic.lock.conflict", 409),
    /** 唯一约束冲突 */
    UNIQUE_CONSTRAINT_VIOLATION("A01062", "unique.constraint.violation", 409),
    /** 资源不存在 */
    DATA_NOT_FOUND("A01063", "data.not.found", 404),
    /** 资源已存在 */
    DATA_ALREADY_EXISTS("A01064", "data.already.exists", 409),

    // ==================== A02 认证异常 ====================

    /** 未授权 */
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
    /** Token 已过期 */
    TOKEN_EXPIRED("A02057", "token.expired", 401),
    /** Token 无效 */
    TOKEN_INVALID("A02058", "token.invalid", 401),

    // ==================== A03 权限异常 ====================

    /** 禁止访问 */
    FORBIDDEN("A03051", "forbidden", 403),
    /** 权限不足 */
    INSUFFICIENT_PERMISSIONS("A03052", "insufficient.permissions", 403),
    /** 数据权限不足 */
    DATA_SCOPE_FORBIDDEN("A03053", "data.scope.forbidden", 403),

    // ==================== A04 数据异常 ====================

    /** 数据唯一性冲突 */
    DB_DUPLICATE_KEY("A04051", "db.duplicate.key", 409),
    /** 数据约束冲突 */
    DB_CONSTRAINT_VIOLATION("A04052", "db.constraint.violation", 400),
    /** 数据完整性错误 */
    DB_DATA_INTEGRITY("A04053", "db.data.integrity", 400),
    /** 数据库查询超时 */
    DB_QUERY_TIMEOUT("A04054", "db.query.timeout", 503),
    /** 数据库连接失败 */
    DB_CONNECTION_FAILED("A04055", "db.connection.failed", 503),
    /** 数据库锁冲突 */
    DB_LOCK_CONTENTION("A04056", "db.lock.contention", 409),

    // ==================== B01 系统异常 ====================

    /** 系统内部错误 */
    INTERNAL_ERROR("B01051", "internal.error", 500),
    /** 服务暂不可用 */
    SERVICE_UNAVAILABLE("B01052", "service.unavailable", 503),
    /** 请求超时 */
    REQUEST_TIMEOUT("B01053", "request.timeout", 408),
    /** 未知错误 */
    UNKNOWN("B01054", "unknown.error", 500),

    // ==================== B02 外部服务异常 ====================

    /** 外部服务调用失败 */
    EXTERNAL_SERVICE_ERROR("B02051", "external.service.error", 502),
    /** 外部服务超时 */
    EXTERNAL_SERVICE_TIMEOUT("B02052", "external.service.timeout", 504),

    // ==================== C01 安全异常 ====================

    /** 越权访问 */
    SECURITY_ACCESS_DENIED("C01051", "security.access.denied", 403),
    /** SQL 注入检测 */
    SECURITY_SQL_INJECTION("C01052", "security.sql.injection", 400),
    /** XSS 攻击检测 */
    SECURITY_XSS_DETECTED("C01053", "security.xss.detected", 400),
    /** CSRF 令牌无效 */
    SECURITY_CSRF_INVALID("C01054", "security.csrf.invalid", 403),
    /** 请求频率超限 */
    RATE_LIMIT("C01055", "rate.limit", 429),
    /** 租户配额超限 */
    QUOTA_EXCEEDED("C01056", "quota.exceeded", 429),
    /** 资源锁冲突 */
    RESOURCE_LOCKED("C01057", "resource.locked", 423),
    /** 账号已锁定 */
    ACCOUNT_LOCKED("C01058", "account.locked", 423),
    /** 需要二次认证 */
    REAUTH_REQUIRED("C01059", "reauth.required", 401),
    /** 双因素认证需要 */
    MFA_REQUIRED("C0105A", "mfa.required", 401);

    private final String code;
    private final String key;
    private final int httpStatus;

    /**
     * 构造函数
     *
     * @param code       异常码
     * @param key        国际化消息键
     * @param httpStatus HTTP 状态码
     */
    UnifiedExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
        // 自动注册到全局注册表
        ExceptionCodeRegistry.register(code, this);
    }

    /**
     * 获取异常级别
     *
     * @return 异常级别
     */
    public ExceptionLevel getLevel() {
        return switch (this) {
            case SUCCESS -> ExceptionLevel.INFO;
            case RATE_LIMIT, QUOTA_EXCEEDED, DB_QUERY_TIMEOUT, REQUEST_TIMEOUT -> ExceptionLevel.WARN;
            case EXTERNAL_SERVICE_ERROR, EXTERNAL_SERVICE_TIMEOUT, SERVICE_UNAVAILABLE,
                 DB_CONNECTION_FAILED -> ExceptionLevel.FATAL;
            default -> ExceptionLevel.ERROR;
        };
    }

    /**
     * 获取异常分类
     *
     * @return 异常分类
     */
    public ExceptionCategory getCategory() {
        return switch (this.name().charAt(0)) {
            case 'A' -> ExceptionCategory.BUSINESS;
            case 'B' -> ExceptionCategory.INFRA;
            case 'C' -> ExceptionCategory.SECURITY;
            default -> ExceptionCategory.BUSINESS;
        };
    }
}
