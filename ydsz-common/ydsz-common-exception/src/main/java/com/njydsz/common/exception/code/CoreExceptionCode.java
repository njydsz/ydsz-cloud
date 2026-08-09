package com.njydsz.common.exception.code;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;
import com.njydsz.common.exception.registry.YdszResultCode;

import lombok.Getter;

/**
 * 核心模块异常码。
 *
 * <p>业务通用码（A01/A04/A06/B01/B02 系列）+ 通用成功/失败码。
 * 覆盖业务逻辑校验、系统内部错误、外部服务调用、文件操作等通用场景。
 *
 * <p>限流相关码已迁移至 {@link RateLimitExceptionCode}，
 * 认证/权限/安全码已迁移至 {@link SecurityExceptionCode}。
 *
 * @author ydsz-team
 * @since 2.0.0
 * @see SecurityExceptionCode
 * @see RateLimitExceptionCode
 */
@Getter
@YdszResultCode(module = "core", description = "核心模块业务异常码")
public enum CoreExceptionCode implements ExceptionCode {

    // ==================== 成功 ====================

    /** 操作成功（原 ResponseCode.SUCCESS 000000） */
    SUCCESS("A00000", "success", 200),

    // ==================== A01 参数/业务异常 ====================

    /** 操作失败（原 ResponseCode.FAIL 111111） */
    FAIL("A01051", "operation.fail", 400),
    /** 参数错误（原 ResponseCode.PARAM_ERROR 100001） */
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
    /** 请求方法不允许（原 ResponseCode.METHOD_NOT_ALLOWED 100405） */
    METHOD_NOT_ALLOWED("A01058", "method.not.allowed", 405),
    /** 重复提交 */
    DUPLICATE_SUBMISSION("A01059", "duplicate.submission", 400),
    /** 流程状态无效 */
    INVALID_FLOW_STATE("A01060", "invalid.flow.state", 400),
    /** 乐观锁冲突/并发冲突 */
    OPTIMISTIC_LOCK_CONFLICT("A01061", "optimistic.lock.conflict", 409),
    /** 唯一约束冲突 */
    UNIQUE_CONSTRAINT_VIOLATION("A01062", "unique.constraint.violation", 409),

    // ==================== A04 数据/资源异常 ====================

    /** 资源不存在（原 ResponseCode.NOT_FOUND 100404） */
    NOT_FOUND("A04051", "not.found", 404),
    /** 资源冲突（原 ResponseCode.CONFLICT 100409） */
    CONFLICT("A04052", "conflict", 409),
    /** 数据未找到 */
    DATA_NOT_FOUND("A04053", "data.not.found", 404),
    /** 资源未找到 */
    RESOURCE_NOT_FOUND("A04054", "resource.not.found", 404),
    /** 数据已存在 */
    DATA_ALREADY_EXISTS("A04055", "data.already.exists", 409),
    /** 数据冲突 */
    DATA_CONFLICT("A04056", "data.conflict", 409),

    // 文件相关
    /** 文件上传失败 */
    FILE_UPLOAD_FAILED("A04061", "file.upload.failed", 500),
    /** 文件下载失败 */
    FILE_DOWNLOAD_FAILED("A04062", "file.download.failed", 500),
    /** 不支持的文件类型 */
    UNSUPPORTED_FILE_TYPE("A04063", "unsupported.file.type", 400),
    /** 文件大小超限 */
    FILE_SIZE_EXCEEDED("A04064", "file.size.exceeded", 400),

    // ==================== B01 系统异常 ====================

    /** 系统内部错误（原 ResponseCode.INTERNAL_ERROR 100500） */
    INTERNAL_ERROR("B01051", "internal.error", 500),
    /** 系统错误 */
    SYSTEM_ERROR("B01052", "system.error", 500),
    /** 数据库错误 */
    DATABASE_ERROR("B01053", "database.error", 500),
    /** 服务不可用（原 ResponseCode.SERVICE_UNAVAILABLE 100503） */
    SERVICE_UNAVAILABLE("B01054", "service.unavailable", 503),
    /** 网络错误 */
    NETWORK_ERROR("B01055", "network.error", 500),
    /** 缓存错误 */
    CACHE_ERROR("B01056", "cache.error", 500),
    /** 消息队列错误 */
    MQ_ERROR("B01057", "mq.error", 500),
    /** 存储错误 */
    STORAGE_ERROR("B01058", "storage.error", 500),
    /** 基础设施服务不可用 */
    INFRA_SERVICE_UNAVAILABLE("B01059", "infrastructure.service.unavailable", 503),
    /** 熔断器开启 */
    CIRCUIT_BREAKER_OPEN("B01060", "circuit.breaker.open", 503),
    /** 资源耗尽 */
    RESOURCE_EXHAUSTED("B01061", "resource.exhausted", 429),
    /** 服务降级 */
    SERVICE_DEGRADED("B01062", "service.degraded", 503),

    // ==================== B02 外部服务异常 ====================

    /** 网关错误（原 ResponseCode.BAD_GATEWAY 100502） */
    BAD_GATEWAY("B02051", "bad.gateway", 502),
    /** 网关超时（原 ResponseCode.GATEWAY_TIMEOUT 100504） */
    GATEWAY_TIMEOUT("B02052", "gateway.timeout", 504),
    /** 其他外部服务错误 */
    OTHER_EXTERNAL_ERROR("B02053", "other.external.error", 502),
    /** 外部服务超时 */
    EXTERNAL_SERVICE_TIMEOUT("B02054", "external.service.timeout", 504),
    /** 外部服务拒绝 */
    EXTERNAL_SERVICE_REJECTED("B02055", "external.service.rejected", 502);

    // ==================== 字段定义 ====================

    /** 异常错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    CoreExceptionCode(String code, String key, int httpStatus) {
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
        for (CoreExceptionCode code : values()) {
            registryMap.put(code.getCode(), code);
        }
        ExceptionCodeRegistry.register(registryMap);
    }

    /**
     * 便捷查找方法：按 code 字符串查找本模块的核心异常码枚举。
     *
     * @param code 异常码字符串
     * @return 对应的 CoreExceptionCode 枚举实例；未找到或非 CoreExceptionCode 返回 null
     */
    public static CoreExceptionCode resolve(String code) {
        ExceptionCode ec = ExceptionCodeRegistry.lookup(code);
        return ec instanceof CoreExceptionCode core ? core : null;
    }
}
