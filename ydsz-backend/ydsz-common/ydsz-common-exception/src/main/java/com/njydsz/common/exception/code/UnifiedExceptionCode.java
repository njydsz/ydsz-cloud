package com.njydsz.common.exception.code;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;

/**
 * 统一异常码枚举
 *
 * <p>整合 ResponseCode（6位纯数字）和 CommExceptionCode（字母+数字）两套编码体系，
 * 采用字母+数字风格，语义更清晰。所有异常场景统一使用此枚举。
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
 * <p><b>模块定义：</b>
 * <ul>
 *   <li>A01xxx - 参数/业务异常（序号从 051 起始，避免与已废弃的 CommExceptionCode 冲突）</li>
 *   <li>A02xxx - 认证异常（序号从 051 起始）</li>
 *   <li>A03xxx - 权限异常（序号从 051 起始）</li>
 *   <li>A04xxx - 数据异常（序号从 051 起始）</li>
 *   <li>B01xxx - 系统异常（序号从 051 起始）</li>
 *   <li>B02xxx - 外部服务异常（序号从 051 起始）</li>
 *   <li>C01xxx - 安全异常（序号从 051 起始）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionCode
 */
@Getter
public enum UnifiedExceptionCode implements ExceptionCode {

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

    // ==================== A04 数据异常 ====================

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

    // 请求频率相关
    /** 请求过于频繁（原 ResponseCode.RATE_LIMIT 100429） */
    RATE_LIMIT("A04057", "rate.limit", 429),
    /** 请求过于频繁（限流） */
    REQUEST_TOO_FREQUENT("A04058", "request.too.frequent", 429),
    /** 操作过于频繁 */
    OPERATION_TOO_FREQUENT("A04059", "operation.too.frequent", 429),
    /** 限流异常 */
    RATE_LIMIT_EXCEEDED("A04060", "rate.limit.exceeded", 429),

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
    EXTERNAL_SERVICE_REJECTED("B02055", "external.service.rejected", 502),

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

    UnifiedExceptionCode(String code, String key, int httpStatus) {
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

    /** 局部缓存，供 resolve() 快速查找 */
    private static final Map<String, UnifiedExceptionCode> CODE_MAP = new HashMap<>();

    static {
        // 本模块自动注册到全局注册中心
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (UnifiedExceptionCode code : values()) {
            registryMap.put(code.getCode(), code);
            CODE_MAP.put(code.getCode(), code);
        }
        ExceptionCodeRegistry.register(registryMap);
    }

    /**
     * 便捷查找方法：按 code 字符串查找本模块的统一异常码枚举
     *
     * <p>与 {@link ExceptionCode#fromCode(String)} 的区别在于：
     * <ul>
     *   <li>此方法仅在 UnifiedExceptionCode 自身范围内查找</li>
     *   <li>返回类型为 UnifiedExceptionCode，无需强转</li>
     *   <li>未找到时返回 null，而非抛出异常</li>
     * </ul>
     *
     * @param code 异常码字符串
     * @return 对应的 UnifiedExceptionCode 枚举实例；未找到返回 null
     */
    public static UnifiedExceptionCode resolve(String code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }
}
