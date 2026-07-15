package com.njydsz.pmis.gateway.config;

/**
 * 网关层内部常量定义
 *
 * <p>网关与下游服务之间约定的内部请求头常量。
 * 网关负责注入这些头，下游服务通过 {@code BaseAuthFilter} 解析。
 *
 * @since 2.2.0
 */
public final class GatewayConstants {

    private GatewayConstants() {
        throw new UnsupportedOperationException("Utility class");
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

    /** 租户 ID 请求头 */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
}
