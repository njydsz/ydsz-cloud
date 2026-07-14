package com.njydsz.pmis.common.exception.code;

/**
 * 公共错误码枚举
 *
 * <p>定义系统级通用错误码，各业务模块可扩展自己的错误码枚举。
 *
 * @author Marvin Lee
 * @since 3.5.0
 */
public enum CommonErrorCode implements UnifiedErrorCode {

    // ===== 通用错误（PM00xxx） =====
    SUCCESS("PM00000", "common.success", 200, "操作成功"),
    INTERNAL_ERROR("PM00001", "common.internal_error", 500, "系统内部错误"),
    SERVICE_UNAVAILABLE("PM00002", "common.service_unavailable", 503, "服务不可用"),
    GATEWAY_TIMEOUT("PM00003", "common.gateway_timeout", 504, "网关超时"),

    // ===== 参数校验错误（PM01xxx） =====
    PARAM_INVALID("PM01001", "common.param.invalid", 400, "参数校验失败"),
    PARAM_MISSING("PM01002", "common.param.missing", 400, "必填参数缺失"),
    PARAM_TYPE_MISMATCH("PM01003", "common.param.type_mismatch", 400, "参数类型不匹配"),

    // ===== 认证授权错误（PM02xxx） =====
    UNAUTHORIZED("PM02001", "common.auth.unauthorized", 401, "未认证"),
    FORBIDDEN("PM02002", "common.auth.forbidden", 403, "无权限"),
    TOKEN_EXPIRED("PM02003", "common.auth.token_expired", 401, "令牌已过期"),
    TOKEN_INVALID("PM02004", "common.auth.token_invalid", 401, "令牌无效"),

    // ===== 资源错误（PM03xxx） =====
    RESOURCE_NOT_FOUND("PM03001", "common.resource.not_found", 404, "资源不存在"),
    RESOURCE_CONFLICT("PM03002", "common.resource.conflict", 409, "资源冲突"),
    RESOURCE_LOCKED("PM03003", "common.resource.locked", 423, "资源已锁定"),

    // ===== 限流降级错误（PM04xxx） =====
    RATE_LIMITED("PM04001", "common.ratelimit.exceeded", 429, "请求过于频繁"),
    CIRCUIT_BREAKER_OPEN("PM04002", "common.circuitbreaker.open", 503, "熔断器已打开"),
    DEGRADE("PM04003", "common.degrade", 503, "服务降级"),

    // ===== 并发错误（PM05xxx） =====
    CONCURRENT_MODIFICATION("PM05001", "common.concurrent.modification", 409, "并发修改冲突"),
    DEADLOCK_DETECTED("PM05002", "common.concurrent.deadlock", 409, "检测到死锁"),

    // ===== 数据库错误（PM06xxx） =====
    DATA_INTEGRITY_VIOLATION("PM06001", "common.data.integrity_violation", 400, "数据完整性违反"),
    DUPLICATE_KEY("PM06002", "common.data.duplicate_key", 409, "唯一键冲突"),

    // ===== 分布式事务错误（PM07xxx） =====
    TRANSACTION_FAILED("PM07001", "common.tx.failed", 500, "事务执行失败"),
    TRANSACTION_COMPENSATION_FAILED("PM07002", "common.tx.compensation_failed", 500, "事务补偿失败"),
    TRANSACTION_TIMEOUT("PM07003", "common.tx.timeout", 504, "事务超时");

    private final String code;
    private final String key;
    private final int httpStatus;
    private final String description;

    CommonErrorCode(String code, String key, int httpStatus, String description) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
        this.description = description;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getDescription() {
        return description;
    }
}
