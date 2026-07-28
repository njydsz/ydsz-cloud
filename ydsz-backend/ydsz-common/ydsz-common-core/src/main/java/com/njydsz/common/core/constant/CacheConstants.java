package com.njydsz.common.core.constant;

/**
 * 缓存名称常量定义。
 *
 * <p>统一管理各业务模块使用的 Spring Cache 缓存名称（cache name），
 * 避免各模块各自硬编码字符串，便于在配置层统一调整 TTL 和缓存策略。
 *
 * <p><b>命名约定：</b>
 * <ul>
 *   <li>格式：{@code MODULE_BUSINESS_SCOPE_CACHE}</li>
 *   <li>示例：{@code FLOW_DEF_PUBLISHED_CACHE} 表示 workflow 模块流程定义已发布版本的缓存</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Cacheable(value = CacheConstants.FLOW_DEF_PUBLISHED_CACHE,
 *            key = "#flowCode + ':' + #version", unless = "#result == null")
 * public FlowDefinition getPublishedDefinition(String flowCode, String version) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CacheConstants {

    private CacheConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ============================== 工作流模块缓存 ==============================

    /**
     * 流程定义已发布版本缓存。
     *
     * <p>缓存键：{@code flowCode:version:tenantId}。
     * 当流程定义发布/下线/删除时通过 {@code @CacheEvict(allEntries=true)} 失效。
     */
    public static final String FLOW_DEF_PUBLISHED_CACHE = "flow_def_published";

    /**
     * 流程定义最新版本缓存。
     *
     * <p>缓存键：{@code flowCode:tenantId}。
     * 当流程定义发布新版本时失效。
     */
    public static final String FLOW_DEF_LATEST_CACHE = "flow_def_latest";

    /**
     * 三方审批账号按用户 ID 查询缓存。
     *
     * <p>缓存键：{@code userId:platform}。
     * 当账号绑定/解绑时失效。
     */
    public static final String FLOW_THIRDPARTY_BY_USER_CACHE = "flow_thirdparty_by_user";

    /**
     * 三方审批账号按 OpenID 查询缓存。
     *
     * <p>缓存键：{@code openId:platform}。
     * 当账号绑定/解绑时失效。
     */
    public static final String FLOW_THIRDPARTY_BY_OPENID_CACHE = "flow_thirdparty_by_openid";

    // ============================== NextWiki 模块缓存 ==============================

    /**
     * NextWiki 文件 ACL 权限缓存。
     *
     * <p>缓存键：{@code fileNodeId:userId}。
     * 当文件权限变更（分享/配额调整）时通过 {@code @CacheEvict(allEntries=true)} 失效。
     */
    public static final String NEXTWIKI_FILE_ACL_CACHE = "nextwiki:file:acl";

    // ============================== System 模块缓存 ==============================

    /**
     * 系统字典项缓存。
     *
     * <p>缓存键：{@code dictTypeCode}。
     * 当字典项增删改时通过 {@code @CacheEvict} 主动失效。
     * 用于高频下拉框渲染场景，避免每次请求触发 N 次 SQL。
     */
    public static final String SYSTEM_DICT_ITEM_CACHE = "system:dict:item";

    /**
     * 系统配置缓存。
     *
     * <p>缓存键：{@code configKey}。
     * 当配置变更时通过 {@code @CacheEvict} 主动失效。TTL 默认 30min。
     */
    public static final String SYSTEM_CONFIG_CACHE = "system:config";

    /**
     * 系统变量缓存。
     *
     * <p>缓存键：{@code variableKey}。
     * 当变量变更时通过 {@code @CacheEvict} 主动失效。
     */
    public static final String SYSTEM_VARIABLE_CACHE = "system:variable";
}
