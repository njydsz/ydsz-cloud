package com.remisoft.common.core.context;

import java.io.Serializable;

/**
 * 请求上下文数据载体
 *
 * <p>作为透明的数据容器，生命周期由 {@link RequestContext} 管理。
 * 所有字段均可为 {@code null}——调用方需自行做 null 判断。
 *
 * <h3>使用场景：</h3>
 * <ul>
 *   <li>跨线程传递上下文快照时，可通过 {@link RequestContext#snapshot()} 序列化此数据结构</li>
 *   <li>单元测试中可构造 mock 数据</li>
 *   <li>不推荐在业务逻辑中直接持有此对象——仍应通过 {@code RequestContext.getTenantId()} 等静态方法访问</li>
 * </ul>
 *
 * @param tenantId             租户 ID
 * @param userId               用户 ID
 * @param traceId              链路追踪 ID
 * @param requestId            请求唯一 ID
 * @param language             客户端语言 (zh-CN / en-US 等)
 * @param tenantIsolationSkipped 是否跳过租户隔离
 * @param clientIp             客户端 IP 地址
 * @param requestSource        请求来源（INTERNAL / OPEN_API / WEB_HOOK）
 * @param apiVersion           API 版本号
 * @author remi-team
 * @since 1.8.0
 */
public record RequestContextData(
    String tenantId,
    String userId,
    String traceId,
    String requestId,
    String language,
    boolean tenantIsolationSkipped,
    String clientIp,
    String requestSource,
    String apiVersion
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 创建空的上下文（所有字段为默认值）
     */
    public RequestContextData() {
        this(null, null, null, null, null, false, null, null, null);
    }

    /**
     * 创建仅含基础审计字段的便捷构造
     *
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param traceId  链路追踪 ID
     */
    public RequestContextData(String tenantId, String userId, String traceId) {
        this(tenantId, userId, traceId, null, null, false, null, null, null);
    }

    // -------------------------------------------------------------------------
    // 派生 API（空安全 + 便捷赋值）
    // -------------------------------------------------------------------------

    /**
     * 返回一个副本，仅 tenantId 字段被替换
     */
    public RequestContextData withTenantId(String newTenantId) {
        return new RequestContextData(
            newTenantId, userId, traceId, requestId, language,
            tenantIsolationSkipped, clientIp, requestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 userId 字段被替换
     */
    public RequestContextData withUserId(String newUserId) {
        return new RequestContextData(
            tenantId, newUserId, traceId, requestId, language,
            tenantIsolationSkipped, clientIp, requestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 traceId 字段被替换
     */
    public RequestContextData withTraceId(String newTraceId) {
        return new RequestContextData(
            tenantId, userId, newTraceId, requestId, language,
            tenantIsolationSkipped, clientIp, requestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 requestId 字段被替换
     */
    public RequestContextData withRequestId(String newRequestId) {
        return new RequestContextData(
            tenantId, userId, traceId, newRequestId, language,
            tenantIsolationSkipped, clientIp, requestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 language 字段被替换
     */
    public RequestContextData withLanguage(String newLanguage) {
        return new RequestContextData(
            tenantId, userId, traceId, requestId, newLanguage,
            tenantIsolationSkipped, clientIp, requestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 tenantIsolationSkipped 字段被替换
     */
    public RequestContextData withTenantIsolationSkipped(boolean newSkipped) {
        return new RequestContextData(
            tenantId, userId, traceId, requestId, language,
            newSkipped, clientIp, requestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 clientIp 字段被替换
     */
    public RequestContextData withClientIp(String newClientIp) {
        return new RequestContextData(
            tenantId, userId, traceId, requestId, language,
            tenantIsolationSkipped, newClientIp, requestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 requestSource 字段被替换
     */
    public RequestContextData withRequestSource(String newRequestSource) {
        return new RequestContextData(
            tenantId, userId, traceId, requestId, language,
            tenantIsolationSkipped, clientIp, newRequestSource, apiVersion
        );
    }

    /**
     * 返回一个副本，仅 apiVersion 字段被替换
     */
    public RequestContextData withApiVersion(String newApiVersion) {
        return new RequestContextData(
            tenantId, userId, traceId, requestId, language,
            tenantIsolationSkipped, clientIp, requestSource, newApiVersion
        );
    }

    /**
     * 深度拷贝当前对象
     */
    public RequestContextData copy() {
        return new RequestContextData(
            tenantId, userId, traceId, requestId, language,
            tenantIsolationSkipped, clientIp, requestSource, apiVersion
        );
    }
}
