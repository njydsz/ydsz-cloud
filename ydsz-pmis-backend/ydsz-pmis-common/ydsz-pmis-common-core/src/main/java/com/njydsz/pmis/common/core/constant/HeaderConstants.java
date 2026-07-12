package com.njydsz.pmis.common.core.constant;

import com.njydsz.pmis.common.core.enums.IdentityType;
import com.njydsz.pmis.common.core.enums.DataScopeType;
import com.njydsz.pmis.common.core.enums.ServiceType;

/**
 * 全局 HTTP 请求头常量定义。
 *
 * <p>约定：
 * <ul>
 *   <li>统一使用 Title Case 风格（如 X-Access-Token）</li>
 *   <li>集合类 header 默认使用 CSV（逗号分隔），也允许多 header 值</li>
 *   <li>表级列规则使用分号分隔不同表（如 {@code table:col1,col2;table2:col3}）</li>
 * </ul>
 *
 * <p>与各模块对应关系：
 * <ul>
 *   <li>ydsz-pmis-common-web：解析请求头，构建 {@link com.njydsz.pmis.common.util.auth.YdszAuthInfo}</li>
 *   <li>ydsz-pmis-common-auth：{@code @RbacDataScope} 切面写入 extra headers</li>
 *   <li>ydsz-pmis-common-feign：透传请求头到下游服务</li>
 *   <li>ydsz-pmis-common-jdbc：SQL 拦截器读取并改写 SQL</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class HeaderConstants {

    private HeaderConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 服务类型。
     *
     * <p>用于区分请求来源服务类型（WEB_SERVICE / APP_SERVICE 等）。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     * @see ServiceType
     */
    public static final String X_SERVICE_TYPE = "X-Service-Type";

    /**
     * 用户系统语言。
     *
     * <p>格式示例：{@code zh-CN}、{@code en-US}。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_USER_LANGUAGE = "X-User-Language";

    /**
     * 用户设备唯一标识。
     *
     * <p>用于设备追踪与多端识别。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_DISTINCT_ID = "X-Distinct-Id";

    /**
     * 身份类型。
     *
     * <p>用于区分公司用户、访客用户、瑞米用户等身份类型。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     * @see IdentityType
     */
    public static final String X_IDENTITY_TYPE = "X-Identity-Type";

    /**
     * 登录访问令牌。
     *
     * <p>用户登录后颁发的 AccessToken，用于身份认证与用户信息加载。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_ACCESS_TOKEN = "X-Access-Token";

    /**
     * 数据权限范围类型。
     *
     * <p>用于决定行级数据权限按哪个维度生效，值为 {@link DataScopeType#getCode()}。
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
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     * @see DataScopeType
     */
    public static final String X_DATA_SCOPE = "X-Data-Scope";

    /**
     * 公司ID集合（CSV）。
     *
     * <p>当数据权限范围为集团类型（GROUP）时，此 header 包含用户可访问的所有公司ID。
     *
     * <p>格式：逗号分隔（如 {@code 1001,1002}），也允许多 header 值合并。
     *
     * <p>对应 scope：{@link DataScopeType#GROUP}
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_COMPANY_IDS = "X-Company-Ids";

    /**
     * 部门ID集合（CSV）。
     *
     * <p>当数据权限范围为公司/部门类型（COMPANY/DEPT）时，此 header 包含用户可访问的所有部门ID。
     *
     * <p>格式：逗号分隔（如 {@code 2001,2002}），也允许多 header 值合并。
     *
     * <p>对应 scope：{@link DataScopeType#COMPANY}、
     * {@link DataScopeType#DEPT}
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_DEPT_IDS = "X-Dept-Ids";

    /**
     * 当前登录用户唯一标识。
     *
     * <p>当数据权限范围为用户类型（USER）时，此 header 作为行级过滤条件。
     *
     * <p>对应 scope：{@link DataScopeType#USER}
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_UNIQUE_ID = "X-Unique-Id";

    /**
     * 租户ID。
     *
     * <p>当数据权限范围为租户类型（TENANT）时，此 header 作为行级过滤条件。
     *
     * <p>对应 scope：{@link DataScopeType#TENANT}
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_TENANT_ID = "X-Tenant-Id";

    /**
     * 项目ID集合（CSV）。
     *
     * <p>当数据权限范围为项目类型（PROJECT）时，此 header 包含用户可访问的所有项目ID。
     *
     * <p>格式：逗号分隔，也允许多 header 值合并。
     *
     * <p>对应 scope：{@link DataScopeType#PROJECT}
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_PROJECT_IDS = "X-Project-Ids";

    /**
     * 区域ID集合（CSV）。
     *
     * <p>当数据权限范围为区域类型（REGION）时，此 header 包含用户可访问的所有区域ID。
     *
     * <p>格式：逗号分隔，也允许多 header 值合并。
     *
     * <p>对应 scope：{@link DataScopeType#REGION}
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
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
     *
     * <p>格式：标识键字符串（如 {@code project_member_scope}、{@code dept_tree_scope}）
     *
     * <p>对应 scope：{@link DataScopeType#CUSTOM}
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_CUSTOM_SQL_CONDITION = "X-Custom-Sql-Condition";

    /**
     * 请求/响应头报文类型。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CONTENT_TYPE = "Content-Type";

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
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
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
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
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
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_COL_PERMISSION_SIGN = "X-Col-Permission-Sign";

    /**
     * 请求来源标识。
     *
     * <p>用于标识请求的来源渠道（如 PC Web / H5 / APP / 小程序）。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
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
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 请求追踪 ID。
     *
     * <p>用于全链路请求追踪，贯穿网关、服务间调用、日志记录等场景。
     * 若请求未携带，由服务端自动生成并写入响应头。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_REQUEST_ID = "X-Request-Id";

    /**
     * HTTP/2 流 ID。
     *
     * <p>用于 HTTP/2 协议下的流标识，支持多路复用场景的请求追踪。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String HTTP2_STREAM_ID = "X-Http2-Stream-Id";

    /**
     * gRPC 追踪头。
     *
     * <p>用于 gRPC 服务间调用的追踪标识，与 HTTP 请求追踪体系保持一致。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String GRPC_TRACE_HEADER = "grpc-trace-bin";

    /**
     * 链路追踪父 ID。
     *
     * <p>用于分布式链路追踪，标识当前请求的父 Span ID。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_TRACE_PARENT = "traceparent";

    /**
     * 链路追踪状态。
     *
     * <p>W3C Trace Context 标准头部，标识追踪状态标志位。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_TRACE_STATE = "tracestate";

    /**
     * 链路追踪 Baggage。
     *
     * <p>W3C Baggage 标准头部，用于在分布式链路中传递自定义键值对。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_BAGGAGE = "baggage";

    /**
     * 用户代理。
     *
     * <p>标识客户端类型、操作系统、浏览器等信息。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String USER_AGENT = "User-Agent";

    /**
     * 授权头。
     *
     * <p>用于传递认证凭据，如 Bearer Token、Basic Auth 等。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String AUTHORIZATION = "Authorization";

    /**
     * 内容长度。
     *
     * <p>请求或响应体的字节长度。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CONTENT_LENGTH = "Content-Length";

    /**
     * 接受编码。
     *
     * <p>客户端支持的响应内容编码方式（如 gzip、deflate）。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String ACCEPT_ENCODING = "Accept-Encoding";

    /**
     * 内容编码。
     *
     * <p>响应体使用的内容编码方式。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CONTENT_ENCODING = "Content-Encoding";

    /**
     * 缓存控制。
     *
     * <p>控制缓存行为，如 no-cache、max-age 等。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CACHE_CONTROL = "Cache-Control";

    /**
     * 跨域来源。
     *
     * <p>标识请求来源的源（scheme、host、port）。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String ORIGIN = "Origin";

    /**
     * 跨域资源共享允许源。
     *
     * <p>响应头，指定允许访问该资源的外部域。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

    /**
     * 跨域资源共享允许方法。
     *
     * <p>响应头，指定允许的 HTTP 方法。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

    /**
     * 跨域资源共享允许头。
     *
     * <p>响应头，指定允许的请求头。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

    /**
     * 内容安全策略。
     *
     * <p>用于防止 XSS 攻击，控制页面可以加载哪些资源。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    /**
     * 严格传输安全。
     *
     * <p>强制浏览器使用 HTTPS 访问站点。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";

    /**
     * X 内容类型选项。
     *
     * <p>防止浏览器进行 MIME 类型嗅探。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    /**
     * X 帧选项。
     *
     * <p>控制页面是否可以被嵌入到 frame/iframe 中，防止点击劫持。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_FRAME_OPTIONS = "X-Frame-Options";

    /**
     * X XSS 保护。
     *
     * <p>启用浏览器的 XSS 过滤器。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String X_XSS_PROTECTION = "X-XSS-Protection";

    /**
     * 引用策略。
     *
     * <p>控制 Referer 头的发送策略。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String REFERRER_POLICY = "Referrer-Policy";

    /**
     * 权限策略。
     *
     * <p>控制浏览器特性（如摄像头、麦克风）的访问权限。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String PERMISSIONS_POLICY = "Permissions-Policy";

    /**
     * 跨域嵌入策略。
     *
     * <p>控制页面是否可以嵌入跨域资源。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CROSS_ORIGIN_EMBEDDER_POLICY = "Cross-Origin-Embedder-Policy";

    /**
     * 跨域打开策略。
     *
     * <p>控制跨域窗口的打开行为。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CROSS_ORIGIN_OPENER_POLICY = "Cross-Origin-Opener-Policy";

    /**
     * 跨域资源策略。
     *
     * <p>控制跨域资源的访问策略。
     *
     * @author Marvin Lee
     * @email limw1888@126.com
     * @version 3.5.0
     */
    public static final String CROSS_ORIGIN_RESOURCE_POLICY = "Cross-Origin-Resource-Policy";
}
