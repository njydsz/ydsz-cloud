package com.njydsz.pmis.common.constant;

/**
 * PMIS 缓存常量（P1-10）
 *
 * <p>统一管理各模块 Spring Cache 名称，避免硬编码字符串散落各处。
 * 与 {@link com.njydsz.pmis.common.config.PmisCacheConfig} 中的 TTL 配置一一对应。
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>{@code @Cacheable(value = CacheConstants.USER_CACHE, ...)} 替代 {@code "user"}</li>
 *   <li>新增缓存名称时，同步在 {@code PmisCacheConfig} 中配置对应 TTL</li>
 *   <li>缓存名称使用小写 + 下划线，与 Redis key 风格一致</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    // ==================== 系统模块 ====================

    /** 系统配置缓存（TTL 10m） */
    public static final String CONFIG_CACHE = "config";

    // ==================== 用户中心模块 ====================

    /** 用户账号缓存（按 ID 查询，TTL 30m） */
    public static final String USER_BY_ID_CACHE = "user:by_id";

    /** 用户账号缓存（按用户名查询，TTL 30m） */
    public static final String USER_BY_USERNAME_CACHE = "user:by_username";

    /** 角色缓存（TTL 1h） */
    public static final String ROLE_CACHE = "role";

    /** 部门缓存（TTL 1h） */
    public static final String DEPT_CACHE = "dept";

    /** 字典缓存（TTL 2h） */
    public static final String DICT_CACHE = "dict";

    /** 全部启用权限缓存（TTL 1h） */
    public static final String PERM_ALL_ENABLED_CACHE = "perm:all_enabled";

    /** 用户权限编码缓存（TTL 1h） */
    public static final String PERM_CODES_CACHE = "perm:codes";

    /** 用户菜单树缓存（TTL 1h） */
    public static final String PERM_MENU_TREE_CACHE = "perm:menu_tree";

    /** 全部菜单树缓存（TTL 1h） */
    public static final String PERM_ALL_MENU_TREE_CACHE = "perm:all_menu_tree";

    /** 通用权限缓存（TTL 1h） */
    public static final String PERMISSION_CACHE = "permission";

    // ==================== 项目模块 ====================

    /** 驾驶舱报表缓存（TTL 5m） */
    public static final String COCKPIT_CACHE = "cockpit:report";

    // ==================== 工作流模块 ====================

    /** 流程定义缓存 - 按已发布版本查询（TTL 30m） */
    public static final String FLOW_DEF_PUBLISHED_CACHE = "flow:def:published";

    /** 流程定义缓存 - 按最新版本查询（TTL 30m） */
    public static final String FLOW_DEF_LATEST_CACHE = "flow:def:latest";

    /** DMN 决策表缓存 - 按 tableKey 查询（TTL 1h，变更频率低） */
    public static final String FLOW_DMN_BY_KEY_CACHE = "flow:dmn:by_key";

    /** 三方账号映射缓存 - 按 platform+openId 查询（TTL 30m） */
    public static final String FLOW_THIRDPARTY_BY_OPENID_CACHE = "flow:thirdparty:by_openid";

    /** 三方账号映射缓存 - 按 userId+platform 查询（TTL 30m） */
    public static final String FLOW_THIRDPARTY_BY_USER_CACHE = "flow:thirdparty:by_user";
}
