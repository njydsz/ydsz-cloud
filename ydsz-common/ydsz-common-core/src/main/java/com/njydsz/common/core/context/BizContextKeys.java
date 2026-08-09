package com.njydsz.common.core.context;

/**
 * 业务级上下文键集中定义（core 去业务化的承载点）。
 *
 * <p>core 作为最底层模块，本身不应承载认证 / 租户 / 数据权限 / 审计等业务语义。
 * 这些<b>业务键</b>统一收敛到本类，由对应的业务模块（auth / tenant / audit / jdbc 等）引用；
 * {@link RequestContext} 中原有的同名常量已 {@code @Deprecated} 并桥接至此，保证已有调用方零改动。</p>
 *
 * <p>各业务模块若需类型安全的访问，建议在本模块内声明各自的
 * {@link ContextKey}（例如 {@code ContextKey<AuthInfo> KEY = ContextKey.of("authInfo", AuthInfo.class)}），
 * 通过 {@link RequestContext#put(ContextKey, Object)} / {@link RequestContext#get(ContextKey)} 读写，
 * 而非依赖 String 常量 + Object 弱引用。</p>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
public final class BizContextKeys {

    private BizContextKeys() {
        throw new UnsupportedOperationException("Constants holder");
    }

    /** 认证信息键（值类型由认证模块定义，core 仅以 Object 承载）。 */
    public static final String KEY_AUTH_INFO = "authInfo";

    /** 登录用户键。 */
    public static final String KEY_LOGIN_USER = "loginUser";

    /** 租户上下文键（tenantId / 系统租户标识 / isSkipIsolation）。 */
    public static final String KEY_TENANT_CONTEXT = "tenantContext";

    /** 列权限信息键。 */
    public static final String KEY_COLUMN_PERMISSION = "columnPermission";

    /** 审计上下文数据键。 */
    public static final String KEY_AUDIT_DATA = "auditData";

    /** HTTP 请求对象键（建议使用 {@link RequestContext#setRequestSnapshot} 的不可变快照替代原生对象）。 */
    public static final String KEY_HTTP_REQUEST = "httpRequest";

    /** 数据权限虚拟请求头键（{@code Map<String, String>}）。 */
    public static final String KEY_EXTRA_HEADERS = "extraHeaders";

    /** 请求级用户信息缓存键。 */
    public static final String KEY_CACHED_USER_INFO_MAP = "cachedUserInfoMap";
}
