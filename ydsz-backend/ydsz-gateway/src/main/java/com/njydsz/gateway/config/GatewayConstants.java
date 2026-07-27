package com.njydsz.gateway.config;

/**
 * 网关层内部常量定义
 *
 * <p>网关与下游服务之间约定的内部请求头常量。
 * 网关负责注入这些头，下游服务通过 {@code BaseAuthFilter} 解析。
 *
 * <h3>使用约束</h3>
 * <ul>
 *   <li>所有 X-User-* / X-Internal-* 头在 {@link com.njydsz.gateway.filter.AuthGlobalFilter}
 *       中统一注入，{@link com.njydsz.gateway.config.PathGuard#internalHeaders()}
 *       中定义需剥离的客户端伪造头集合</li>
 *   <li>新增内部头时必须同步更新 PathGuard 列表 + 下游 BaseAuthFilter 解析逻辑</li>
 *   <li>下游服务信任网关的前提是 {@link com.njydsz.gateway.config.InternalHeaderSigner}
 *       签名校验通过</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class GatewayConstants {

    private GatewayConstants() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }

    /** 链路追踪 ID 请求头 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 用户 ID 请求头 */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 用户名请求头 */
    public static final String HEADER_USERNAME = "X-Username";

    /** 用户角色请求头（CSV） */
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    /** 用户权限请求头（CSV） */
    public static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";

    /** 内部头签名请求头 */
    public static final String HEADER_INTERNAL_SIG = "X-Internal-Sig";

    /** 内部头签名时间戳请求头 */
    public static final String HEADER_INTERNAL_TS = "X-Internal-Ts";

    /**
     * 内部头签名 nonce 请求头（P0-6 防重放）。
     *
     * <p>网关为每个请求生成唯一 nonce，纳入 HMAC 签名 payload 后透传给下游。
     * 下游服务使用 {@code NonceCache.verifyAndConsume(nonce)} 校验是否重复，
     * 配合时间戳窗口形成"一次性签名"机制。
     */
    public static final String HEADER_INTERNAL_NONCE = "X-Internal-Nonce";

    /** 租户 ID 请求头 */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
}
