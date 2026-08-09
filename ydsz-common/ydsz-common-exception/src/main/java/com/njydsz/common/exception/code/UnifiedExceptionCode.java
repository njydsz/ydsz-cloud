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
 * @deprecated 自 v2.0.0 起，按职责拆分为：
 *             <ul>
 *               <li>{@link CoreExceptionCode} — 业务通用码（A01/A04/A06/B01/B02 系列 + 成功/失败码）</li>
 *               <li>{@link SecurityExceptionCode} — 认证/授权/安全码（A02/A03/C01 系列）</li>
 *               <li>{@link RateLimitExceptionCode} — 限流/熔断/降级码（A04xx 频率控制系列）</li>
 *             </ul>
 *             当前枚举保持完整以确保向后兼容，新代码请使用上述拆分后的枚举。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionCode
 * @see CoreExceptionCode
 * @see SecurityExceptionCode
 * @see RateLimitExceptionCode
 */
@Deprecated(since = "2.0.0", forRemoval = false)
@Getter
public enum UnifiedExceptionCode implements ExceptionCode {

    // ==================== 成功 ====================

    /** 操作成功（原 ResponseCode.SUCCESS 000000） */
    @Deprecated(since = "2.0.0") SUCCESS("A00000", "success", 200),

    // ==================== A01 参数/业务异常 ====================

    /** 操作失败（原 ResponseCode.FAIL 111111） */
    @Deprecated(since = "2.0.0") FAIL("A01051", "operation.fail", 400),
    /** 参数错误（原 ResponseCode.PARAM_ERROR 100001） */
    @Deprecated(since = "2.0.0") PARAM_ERROR("A01052", "param.error", 400),
    /** 非法参数 */
    @Deprecated(since = "2.0.0") ILLEGAL_ARGUMENT("A01053", "illegal.argument", 400),
    /** 请求格式无效 */
    @Deprecated(since = "2.0.0") INVALID_REQUEST_FORMAT("A01054", "invalid.request.format", 400),
    /** 业务状态无效 */
    @Deprecated(since = "2.0.0") INVALID_BUSINESS_STATE("A01055", "invalid.business.state", 400),
    /** 业务规则违反 */
    @Deprecated(since = "2.0.0") BUSINESS_RULE_VIOLATION("A01056", "business.rule.violation", 400),
    /** 通用业务错误 */
    @Deprecated(since = "2.0.0") BUSINESS_ERROR("A01057", "business.error", 400),
    /** 请求方法不允许（原 ResponseCode.METHOD_NOT_ALLOWED 100405） */
    @Deprecated(since = "2.0.0") METHOD_NOT_ALLOWED("A01058", "method.not.allowed", 405),
    /** 重复提交 */
    @Deprecated(since = "2.0.0") DUPLICATE_SUBMISSION("A01059", "duplicate.submission", 400),
    /** 流程状态无效 */
    @Deprecated(since = "2.0.0") INVALID_FLOW_STATE("A01060", "invalid.flow.state", 400),
    /** 乐观锁冲突/并发冲突 */
    @Deprecated(since = "2.0.0") OPTIMISTIC_LOCK_CONFLICT("A01061", "optimistic.lock.conflict", 409),
    /** 唯一约束冲突 */
    @Deprecated(since = "2.0.0") UNIQUE_CONSTRAINT_VIOLATION("A01062", "unique.constraint.violation", 409),

    // ==================== A02 认证异常 ====================

    /** 未授权（原 ResponseCode.UNAUTHORIZED 100401） */
    @Deprecated(since = "2.0.0") UNAUTHORIZED("A02051", "unauthorized", 401),
    /** 未登录 */
    @Deprecated(since = "2.0.0") NOT_LOGGED_IN("A02052", "not.logged.in", 401),
    /** 会话过期 */
    @Deprecated(since = "2.0.0") SESSION_EXPIRED("A02053", "session.expired", 401),
    /** 认证失败 */
    @Deprecated(since = "2.0.0") AUTHENTICATION_FAILED("A02054", "authentication.failed", 401),
    /** 账号已禁用 */
    @Deprecated(since = "2.0.0") ACCOUNT_DISABLED("A02055", "account.disabled", 401),
    /** 账号在其他地方登录 */
    @Deprecated(since = "2.0.0") ACCOUNT_LOGGED_ELSEWHERE("A02056", "account.logged.elsewhere", 401),

    // ==================== A03 权限异常 ====================

    /** 禁止访问（原 ResponseCode.FORBIDDEN 100403） */
    @Deprecated(since = "2.0.0") FORBIDDEN("A03051", "forbidden", 403),
    /** 权限不足 */
    @Deprecated(since = "2.0.0") INSUFFICIENT_PERMISSIONS("A03052", "insufficient.permissions", 403),
    /** 访问被拒绝 */
    @Deprecated(since = "2.0.0") ACCESS_DENIED("A03053", "access.denied", 403),
    /** 角色不匹配 */
    @Deprecated(since = "2.0.0") ROLE_MISMATCH("A03054", "role.mismatch", 403),

    // ==================== A04 数据异常 ====================

    /** 资源不存在（原 ResponseCode.NOT_FOUND 100404） */
    @Deprecated(since = "2.0.0") NOT_FOUND("A04051", "not.found", 404),
    /** 资源冲突（原 ResponseCode.CONFLICT 100409） */
    @Deprecated(since = "2.0.0") CONFLICT("A04052", "conflict", 409),
    /** 数据未找到 */
    @Deprecated(since = "2.0.0") DATA_NOT_FOUND("A04053", "data.not.found", 404),
    /** 资源未找到 */
    @Deprecated(since = "2.0.0") RESOURCE_NOT_FOUND("A04054", "resource.not.found", 404),
    /** 数据已存在 */
    @Deprecated(since = "2.0.0") DATA_ALREADY_EXISTS("A04055", "data.already.exists", 409),
    /** 数据冲突 */
    @Deprecated(since = "2.0.0") DATA_CONFLICT("A04056", "data.conflict", 409),

    // 请求频率相关
    /** 请求过于频繁（原 ResponseCode.RATE_LIMIT 100429） */
    @Deprecated(since = "2.0.0") RATE_LIMIT("A04057", "rate.limit", 429),
    /** 请求过于频繁（限流） */
    @Deprecated(since = "2.0.0") REQUEST_TOO_FREQUENT("A04058", "request.too.frequent", 429),
    /** 操作过于频繁 */
    @Deprecated(since = "2.0.0") OPERATION_TOO_FREQUENT("A04059", "operation.too.frequent", 429),
    /** 限流异常 */
    @Deprecated(since = "2.0.0") RATE_LIMIT_EXCEEDED("A04060", "rate.limit.exceeded", 429),

    // 文件相关
    /** 文件上传失败 */
    @Deprecated(since = "2.0.0") FILE_UPLOAD_FAILED("A04061", "file.upload.failed", 500),
    /** 文件下载失败 */
    @Deprecated(since = "2.0.0") FILE_DOWNLOAD_FAILED("A04062", "file.download.failed", 500),
    /** 不支持的文件类型 */
    @Deprecated(since = "2.0.0") UNSUPPORTED_FILE_TYPE("A04063", "unsupported.file.type", 400),
    /** 文件大小超限 */
    @Deprecated(since = "2.0.0") FILE_SIZE_EXCEEDED("A04064", "file.size.exceeded", 400),

    // ==================== B01 系统异常 ====================

    /** 系统内部错误（原 ResponseCode.INTERNAL_ERROR 100500） */
    @Deprecated(since = "2.0.0") INTERNAL_ERROR("B01051", "internal.error", 500),
    /** 系统错误 */
    @Deprecated(since = "2.0.0") SYSTEM_ERROR("B01052", "system.error", 500),
    /** 数据库错误 */
    @Deprecated(since = "2.0.0") DATABASE_ERROR("B01053", "database.error", 500),
    /** 服务不可用（原 ResponseCode.SERVICE_UNAVAILABLE 100503） */
    @Deprecated(since = "2.0.0") SERVICE_UNAVAILABLE("B01054", "service.unavailable", 503),
    /** 网络错误 */
    @Deprecated(since = "2.0.0") NETWORK_ERROR("B01055", "network.error", 500),
    /** 缓存错误 */
    @Deprecated(since = "2.0.0") CACHE_ERROR("B01056", "cache.error", 500),
    /** 消息队列错误 */
    @Deprecated(since = "2.0.0") MQ_ERROR("B01057", "mq.error", 500),
    /** 存储错误 */
    @Deprecated(since = "2.0.0") STORAGE_ERROR("B01058", "storage.error", 500),
    /** 基础设施服务不可用 */
    @Deprecated(since = "2.0.0") INFRA_SERVICE_UNAVAILABLE("B01059", "infrastructure.service.unavailable", 503),
    /** 熔断器开启 */
    @Deprecated(since = "2.0.0") CIRCUIT_BREAKER_OPEN("B01060", "circuit.breaker.open", 503),
    /** 资源耗尽 */
    @Deprecated(since = "2.0.0") RESOURCE_EXHAUSTED("B01061", "resource.exhausted", 429),
    /** 服务降级 */
    @Deprecated(since = "2.0.0") SERVICE_DEGRADED("B01062", "service.degraded", 503),

    // ==================== B02 外部服务异常 ====================

    /** 网关错误（原 ResponseCode.BAD_GATEWAY 100502） */
    @Deprecated(since = "2.0.0") BAD_GATEWAY("B02051", "bad.gateway", 502),
    /** 网关超时（原 ResponseCode.GATEWAY_TIMEOUT 100504） */
    @Deprecated(since = "2.0.0") GATEWAY_TIMEOUT("B02052", "gateway.timeout", 504),
    /** 其他外部服务错误 */
    @Deprecated(since = "2.0.0") OTHER_EXTERNAL_ERROR("B02053", "other.external.error", 502),
    /** 外部服务超时 */
    @Deprecated(since = "2.0.0") EXTERNAL_SERVICE_TIMEOUT("B02054", "external.service.timeout", 504),
    /** 外部服务拒绝 */
    @Deprecated(since = "2.0.0") EXTERNAL_SERVICE_REJECTED("B02055", "external.service.rejected", 502),

    // ==================== C01 安全异常 ====================

    /** 安全访问被拒绝 */
    @Deprecated(since = "2.0.0") SEC_ACCESS_DENIED("C01051", "security.access.denied", 403),
    /** 需要认证 */
    @Deprecated(since = "2.0.0") AUTHENTICATION_REQUIRED("C01052", "security.authentication.required", 401),
    /** Token过期 */
    @Deprecated(since = "2.0.0") TOKEN_EXPIRED("C01053", "security.token.expired", 401),
    /** 权限拒绝 */
    @Deprecated(since = "2.0.0") PERMISSION_DENIED("C01054", "security.permission.denied", 403);

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

    static {
        // 本模块自动注册到全局注册中心，供 ExceptionCodeRegistry.lookup() 统一反查
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (UnifiedExceptionCode code : values()) {
            registryMap.put(code.getCode(), code);
        }
        ExceptionCodeRegistry.register(registryMap);
    }

    /**
     * 便捷查找方法：按 code 字符串查找本模块的统一异常码枚举
     *
     * <p>内部委托全局注册中心 {@link ExceptionCodeRegistry#lookup(String)}，
     * 避免维护双层缓存（静态度 + 注册表查找）造成空间浪费与数据不一致风险。
     *
     * <p>与其他业务模块 {@link ExceptionCode} 实现相比，此方法额外约束返回类型，
     * 调用方无需强转即可安全使用。
     *
     * @deprecated 使用 {@link CoreExceptionCode#resolve(String)}、
     *             {@link SecurityExceptionCode#resolve(String)}、
     *             {@link RateLimitExceptionCode#resolve(String)} 替代。
     *
     * @param code 异常码字符串
     * @return 对应的 UnifiedExceptionCode 枚举实例；未找到或非 UnifiedExceptionCode 返回 null
     */
    @Deprecated(since = "2.0.0")
    public static UnifiedExceptionCode resolve(String code) {
        ExceptionCode ec = ExceptionCodeRegistry.lookup(code);
        return ec instanceof UnifiedExceptionCode unified ? unified : null;
    }
}
