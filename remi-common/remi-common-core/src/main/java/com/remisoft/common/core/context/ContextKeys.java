package com.remisoft.common.core.context;

/**
 * 预定义上下文键常量库。
 *
 * <p>集中管理内置上下文的 {@link ContextKey} 实例，消除业务模块中的字符串硬编码，
 * 保证键名一致性、避免编译期无法校验的魔法字符串散弹式修改风险。</p>
 *
 * <p>对标参考：</p>
 * <ul>
 *   <li>Spring Cloud Sleuth {@code BaggageFields}</li>
 *   <li>Netty {@code AttributeKey}</li>
 * </ul>
 *
 * <h3>内置键使用示例</h3>
 * <pre>{@code
 * // 写入
 * RequestContext.put(ContextKeys.USER_ID, 12345L);
 * RequestContext.put(ContextKeys.TENANT_ID, "tenant-001");
 *
 * // 读取
 * Long userId = RequestContext.get(ContextKeys.USER_ID);          // Long 类型，无需强转
 * String tenantId = RequestContext.get(ContextKeys.TENANT_ID);   // String 类型
 * }</pre>
 *
 * <h3>自定义键扩展</h3>
 * <pre>{@code
 * // 业务模块自定义键
 * public static final ContextKey<String> APP_ID = ContextKey.ofString("appId");
 * public static final ContextKey<Integer> BUSINESS_LINE = ContextKey.ofInt("businessLine");
 * }</pre>
 *
 * @author remi-team
 * @since 1.7.0
 * @see ContextKey
 * @see RequestContext
 */
public final class ContextKeys {

    private ContextKeys() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 用户 ID。
     *
     * <p>登录成功后由认证过滤器写入，贯穿请求处理全链路。
     * 类型为 {@link String}，与各业务模块的用户 ID 字段类型统一。</p>
     *
     * <p>对应常量：{@link RequestContext#KEY_USER_ID}</p>
     */
    public static final ContextKey<String> USER_ID = ContextKey.ofString(RequestContext.KEY_USER_ID);

    /**
     * 租户 ID。
     *
     * <p>多租户场景下的当前请求租户标识，由租户拦截器写入。
     * 供 SQL 拦截器自动注入 tenant_id 条件。</p>
     *
     * <p>对应常量：{@link RequestContext#KEY_TENANT_ID}</p>
     */
    public static final ContextKey<String> TENANT_ID = ContextKey.ofString(RequestContext.KEY_TENANT_ID);

    /**
     * 链路追踪 ID。
     *
     * <p>由入口过滤器或网关生成并写入，贯穿所有下游调用。
     * 与 {@link com.remisoft.common.core.constant.HeaderConstants#TRACE_ID_HEADER} HTTP header 对应。</p>
     */
    public static final ContextKey<String> TRACE_ID = ContextKey.ofString(RequestContext.KEY_TRACE_ID);

    /**
     * 请求 ID。
     *
     * <p>单次请求的唯一标识，用于日志关联和问题排查。
     * 区别于 traceId：traceId 贯通多个服务的调用链，requestId 仅标识单次入口请求。</p>
     */
    public static final ContextKey<String> REQUEST_ID = ContextKey.ofString(RequestContext.KEY_REQUEST_ID);

    /**
     * 语言区域。
     *
     * <p>示例值：{@code zh-CN}、{@code en-US}。
     * 由国际化拦截器从 HTTP header 或用户配置中读取并写入，
     * 供 i18n 消息解析器使用。</p>
     */
    public static final ContextKey<String> LANGUAGE = ContextKey.ofString(RequestContext.KEY_LANGUAGE);

    /**
     * 租户隔离跳过标记。
     *
     * <p>当 Web 层拦截器判断当前请求 URL 在白名单中时，设为 {@code true}，
     * SQL 拦截器将不注入 tenant_id 条件。类型为 {@link Boolean}。</p>
     */
    public static final ContextKey<Boolean> TENANT_ISOLATION_SKIPPED =
            ContextKey.ofBoolean(RequestContext.KEY_TENANT_ISOLATION_SKIPPED);
}
