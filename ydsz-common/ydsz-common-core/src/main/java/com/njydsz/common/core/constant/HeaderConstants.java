package com.njydsz.common.core.constant;

/**
 * 公共模块 HTTP 请求头常量定义（核心层保留通用协议常量）。
 *
 * <p>仅定义项目通用的请求头名称（链路追踪、网络信息），标准 HTTP 头（如 {@code Content-Type}、{@code Authorization}）
 * 直接在代码中使用字符串字面量即可，无需在此定义常量。
 *
 * <p>约定：
 * <ul>
 *   <li>统一使用 Title Case 风格（如 X-Access-Token）</li>
 *   <li>集合类 header 默认使用 CSV（逗号分隔），也允许多 header 值</li>
 *   <li>表级列规则使用分号分隔不同表（如 {@code table:col1,col2;table2:col3}）</li>
 * </ul>
 *
 * <p><b>v1.11 组织调整：</b>业务域请求头已下沉到各自模块中维护：
 * <ul>
 *   <li><b>认证/身份头</b>：迁移至 {@code ydsz-common-auth} — {@code AuthHeaderConstants}</li>
 *   <li><b>数据权限头</b>：迁移至 {@code ydsz-common-tenant/jdbc} — {@code DataPermissionHeaderConstants}</li>
 *   <li><b>网关内部签名头</b>：迁移至 {@code ydsz-gateway} — {@code InternalSignatureHeaderConstants}</li>
 * </ul>
 *
 * <p>本模块保留以下<b>协议级/通用</b>常量：链踪（TRACE_ID/W3C_TRACEPARENT）、网络（X-Forwarded-For/X-Request-Id）、幂等键。
 * 业务域常量仅作 {@code @Deprecated} 桥接，@since 标记标注迁移版本。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class HeaderConstants {

    private HeaderConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * HTTP 标准授权头（RFC 7235 / RFC 6750）。
     *
     * <p>值 {@code Authorization}，用于承载 OAuth2 Bearer Token、Basic 等标准授权凭证。
     * 统一项目中 Authorization 头的引用，消除 {@code TokenConstants} 与 {@code HeaderConstants} 双源重复定义。</p>
     *
     * @since 1.11.0
     */
    public static final String AUTHORIZATION = "Authorization";

    // ============================== 认证 / 身份 ==============================
    // v1.11 迁移至 ydsz-common-auth AuthHeaderConstants；此处保留 @Deprecated 桥接

    /**
     * 登录访问令牌。
     *
     * <p>用户登录后颁发的 AccessToken，用于身份认证与用户信息加载。
     *
     * @deprecated v1.11 迁移至 {@code com.njydsz.common.auth.constant.AuthHeaderConstants#X_ACCESS_TOKEN}
     */
    @Deprecated(since = "1.11", forRemoval = false)
    public static final String X_ACCESS_TOKEN = "X-Access-Token";

    /**
     * 当前登录用户ID。
     *
     * <p>由网关/认证服务在请求入口写入，贯穿全链路用于身份标识、审计、数据权限过滤。
     * 业务代码优先通过 {@link com.njydsz.common.core.context.RequestContext#getUserId()} 获取，
     * 仅在需要从原生 HTTP 请求直接读取时使用此常量。
     *
     * @since 1.2.0
     * @deprecated v1.11 迁移至 {@code com.njydsz.common.auth.constant.AuthHeaderConstants#X_USER_ID}
     */
    @Deprecated(since = "1.11", forRemoval = false)
    public static final String X_USER_ID = "X-User-Id";

    /**
     * 用户系统语言。
     *
     * <p>格式示例：{@code zh-CN}、{@code en-US}。
     *
     * @deprecated v1.11 迁移至 {@code com.njydsz.common.auth.constant.AuthHeaderConstants#X_USER_LANGUAGE}
     */
    @Deprecated(since = "1.11", forRemoval = false)
    public static final String X_USER_LANGUAGE = "X-User-Language";

    /**
     * 用户设备唯一标识。
     *
     * <p>用于设备追踪与多端识别。
     *
     * @deprecated v1.11 迁移至 {@code com.njydsz.common.auth.constant.AuthHeaderConstants#X_DISTINCT_ID}
     */
    @Deprecated(since = "1.11", forRemoval = false)
    public static final String X_DISTINCT_ID = "X-Distinct-Id";

    /**
     * 身份类型。
     *
     * <p>用于区分公司用户、访客用户、ydsz用户等身份类型。
     *
     * @deprecated v1.11 迁移至 {@code com.njydsz.common.auth.constant.AuthHeaderConstants#X_IDENTITY_TYPE}
     */
    @Deprecated(since = "1.11", forRemoval = false)
    public static final String X_IDENTITY_TYPE = "X-Identity-Type";

    /**
     * 用户名 HTTP 头。
     *
     * <p>由网关在认证后写入，标识当前登录用户的显示名。
     *
     * @since 1.2.0
     * @deprecated v1.11 迁移至 {@code com.njydsz.common.auth.constant.AuthHeaderConstants#X_USERNAME}
     */
    @Deprecated(since = "1.11", forRemoval = false)
    public static final String X_USERNAME = "X-Username";

    /**
     * 用户角色集合 HTTP 头（CSV）。
     *
     * <p>逗号分隔的角色编码列表，用于下游 RBAC 权限判断。
     *
     * @since 1.2.0
     * @deprecated v1.11 迁移至 {@code com.njydsz.common.auth.constant.AuthHeaderConstants#X_USER_ROLES}
     */
    @Deprecated(since = "1.11", forRemoval = false)
    public static final String X_USER_ROLES = "X-User-Roles";

    /**
     * 用户权限集合 HTTP 头（CSV）。
     *
     * <p>逗号分隔的权限编码列表，用于下游细粒度权限判断。
     *
     * @since 1.2.0
     */
    public static final String X_USER_PERMISSIONS = "X-User-Permissions";

    /**
     * 服务类型。
     *
     * <p>用于区分请求来源服务类型（WEB_SERVICE / APP_SERVICE 等）。
     *

     */
    public static final String X_SERVICE_TYPE = "X-Service-Type";

    /**
     * 幂等键。
     *
     * <p>客户端通过此 Header 传递幂等键，服务端据此保证操作幂等性。
     * 参考 Stripe API 的 Idempotency-Key 设计。
     *
     * @since 1.5.0
     */
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    // ============================== 数据权限 ==============================

    /**
     * 数据权限范围类型。
     *
     * <p>配合维度ID类 header 使用：
     * <ul>
     *   <li>tenant：配合 {@link #X_TENANT_ID}</li>
     *   <li>group：配合 {@link #X_COMPANY_IDS}</li>
     *   <li>company/dept：配合 {@link #X_DEPT_IDS}</li>
     *   <li>user：配合 {@link #X_UNIQUE_ID}</li>
     *   <li>project：配合 {@link #X_PROJECT_IDS}</li>
     *   <li>region：配合 {@link #X_REGION_IDS}</li>
     * </ul>
     *
     * <p>当此 header 存在时，SQL 拦截器优先按该 scope 对应维度过滤；
     * 当不携带时，拦截器会按所有非空维度叠加（取交集）。
     *

     */
    public static final String X_DATA_SCOPE = "X-Data-Scope";

    /**
     * 租户ID。
     *
     * <p>当数据权限范围为租户类型（TENANT）时，此 header 作为行级过滤条件。
     */
    public static final String X_TENANT_ID = "X-Tenant-Id";

    /**
     * 当前登录用户唯一标识。
     *
     * <p>当数据权限范围为用户类型（USER）时，此 header 作为行级过滤条件。
     */
    public static final String X_UNIQUE_ID = "X-Unique-Id";

    /**
     * 公司ID集合（CSV）。
     *
     * <p>当数据权限范围为集团类型（GROUP）时，此 header 包含用户可访问的所有公司ID。
     *
     * <p>格式：逗号分隔（如 {@code 1001,1002}），也允许多 header 值合并。
     */
    public static final String X_COMPANY_IDS = "X-Company-Ids";

    /**
     * 部门ID集合（CSV）。
     *
     * <p>当数据权限范围为公司/部门类型（COMPANY/DEPT）时，此 header 包含用户可访问的所有部门ID。
     *
     * <p>格式：逗号分隔（如 {@code 2001,2002}），也允许多 header 值合并。
     */
    public static final String X_DEPT_IDS = "X-Dept-Ids";

    /**
     * 项目ID集合（CSV）。
     *
     * <p>当数据权限范围为项目类型（PROJECT）时，此 header 包含用户可访问的所有项目ID。
     *
     * <p>格式：逗号分隔，也允许多 header 值合并。
     */
    public static final String X_PROJECT_IDS = "X-Project-Ids";

    /**
     * 区域ID集合（CSV）。
     *
     * <p>当数据权限范围为区域类型（REGION）时，此 header 包含用户可访问的所有区域ID。
     *
     * <p>格式：逗号分隔，也允许多 header 值合并。
     */
    public static final String X_REGION_IDS = "X-Region-Ids";

    /**
     * 自定义 SQL 条件标识。
     *
     * <p>当数据权限范围为自定义类型（CUSTOM）时，此 header 携带自定义数据权限的标识键，
     * 由服务端数据权限 Provider 根据此标识生成安全的 SQL 条件片段。
     *
     * <p><b>安全警告：</b>此 header 仅传递标识键，不直接传递 SQL 片段。
     * SQL 条件由服务端 Provider 生成，禁止将原始 SQL 通过 HTTP 请求传入，
     * 以防止 SQL 注入攻击。
     */
    public static final String X_CUSTOM_SQL_CONDITION = "X-Custom-Sql-Condition";

    // ============================== 列级权限 ==============================

    /**
     * 列级权限：表级可见列规则。
     *
     * <p>控制 SELECT 查询中哪些列对当前用户可见。
     *
     * <p>格式：{@code table:col1,col2;table2:col3,col4}
     * <ul>
     *   <li>分号 {@code ;} 分隔不同表</li>
     *   <li>冒号 {@code :} 分隔表名和列名</li>
     *   <li>逗号 {@code ,} 分隔同表多列</li>
     *   <li>表名和列名均小写比对</li>
     * </ul>
     *
     * <p>SQL 拦截器行为：
     * <ul>
     *   <li>当 SELECT 包含 {@code *} 或 {@code t.*} 时，替换为允许列清单</li>
     *   <li>当 SELECT 明确列出列时，仅保留允许的列</li>
     *   <li>若规则为空或不包含某表，表示全部可见（不过滤）</li>
     * </ul>
     *
     * <p>示例：{@code sys_user:id,name,email;sys_role:id,role_name}
     */
    public static final String X_VISIBLE_COLUMNS = "X-Visible-Columns";

    /**
     * 列级权限：表级可编辑列规则。
     *
     * <p>控制 INSERT/UPDATE 操作中哪些列对当前用户可写。
     *
     * <p>格式：同 {@link #X_VISIBLE_COLUMNS}
     *
     * <p>SQL 拦截器行为：
     * <ul>
     *   <li>INSERT/UPDATE 时过滤掉不可编辑的列</li>
     *   <li>若某表没有任何可编辑列，抛出异常阻断写入</li>
     *   <li>若规则为空或不包含某表，表示全部可编辑（不过滤）</li>
     * </ul>
     *
     * <p>示例：{@code sys_user:name,email,phone;sys_role:role_name,description}
     */
    public static final String X_EDITABLE_COLUMNS = "X-Editable-Columns";

    /**
     * 列级权限：签名值。
     *
     * <p>用于对列权限数据（X-Visible-Columns / X-Editable-Columns）进行 HMAC-SHA256 签名校验，
     * 防止攻击者伪造或篡改列权限 Header。
     *
     * <p>签名算法：HMAC-SHA256(visibleColumns + "|" + editableColumns, appSecret)
     *
     * <p>服务端收到请求后，会使用相同的 AppSecret 重新计算签名并与此 Header 值对比，
     * 签名不匹配时将拒绝请求并记录安全审计日志。
     */
    public static final String X_COL_PERMISSION_SIGN = "X-Col-Permission-Sign";

    // ============================== 链路追踪 ==============================

    /**
     * 请求唯一标识 HTTP 头。
     *
     * <p>值为 {@code "X-Request-Id"}，由网关在请求入口自动生成并写入，
     * 用于请求在全生命周期中的唯一标识与故障排查。
     * 与 {@link #TRACE_ID_HEADER} 的区别：X-Request-Id 由本系统产生，
     * X-Trace-Id 兼容 SkyWalking / Jaeger 等外部链路追踪系统。
     *
     * @since 1.2.0
     */
    public static final String X_REQUEST_ID = "X-Request-Id";

    /**
     * 请求追踪 ID HTTP 头。
     *
     * <p>值为 {@code "X-Trace-Id"}，用于全链路请求追踪，
     * 贯穿网关、服务间调用、日志记录等场景。
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * TraceId 在 SLF4J MDC 中的 key 名称。
     *
     * <p>日志框架通过此 key 从 MDC 中提取 traceId 注入日志输出格式。
     */
    public static final String MDC_TRACE_ID_KEY = "traceId";

    /**
     * W3C Trace Context 标准的 traceparent header 名称。
     *
     * <p>格式：{@code 00-{traceId}-{spanId}-01}，用于对接 SkyWalking/Jaeger/Zipkin
     * 等主流分布式链路追踪系统。
     *
     * @since 1.5.0
     * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
     */
    public static final String W3C_TRACEPARENT = "traceparent";

    /**
     * W3C Trace Context 标准的 tracestate header 名称。
     *
     * <p>用于传递供应商特定的追踪上下文信息。
     *
     * @since 1.5.0
     * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
     */
    public static final String W3C_TRACESTATE = "tracestate";

    // ============================== 网关内部签名 ==============================

    /**
     * 网关内部签名 HTTP 头。
     *
     * <p>网关使用 HMAC-SHA256 对请求头签名后写入此头，下游服务通过
     * {@code InternalHeaderSigner.verify()} 校验签名合法性，
     * 防止客户端伪造身份头。
     *
     * @since 1.2.0
     */
    public static final String X_INTERNAL_SIG = "X-Internal-Sig";

    /**
     * 网关内部签名时间戳 HTTP 头。
     *
     * <p>签名的时间戳（毫秒），与 {@link #X_INTERNAL_SIG} 配套使用，
     * 用于防重放窗口校验。
     *
     * @since 1.2.0
     */
    public static final String X_INTERNAL_TS = "X-Internal-Ts";

    /**
     * 网关内部签名 nonce HTTP 头（防重放）。
     *
     * <p>网关为每个请求生成的唯一随机数，纳入 HMAC 签名 payload。
     * 下游服务使用 NonceCache 校验是否已消费过，配合时间戳窗口形成"一次性签名"机制。
     *
     * @since 1.2.0
     */
    public static final String X_INTERNAL_NONCE = "X-Internal-Nonce";

    // ============================== 网络信息 ==============================

    /**
     * 请求来源标识。
     *
     * <p>用于标识请求的来源渠道（如 PC Web / H5 / APP / 小程序）。
     */
    public static final String X_REQUEST_SOURCE = "X-Request-Source";

    /**
     * 请求来源 IP。
     *
     * <p>用于服务间透传客户端真实 IP。通常由网关/负载均衡写入；
     * 若不存在，可由服务端根据 HttpServletRequest 获取并补齐。
     *
     * <p>区别于标准的 {@code X-Forwarded-For}（支持多段链路 IP），
     * 本系统约定使用单值，作为"客户端 IP"的透传载体。
     */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
}
