package com.njydsz.userinfo.server.constant;

/**
 * 缓存名称常量（userinfo 模块本地版本）
 *
 * <p>原参考实现位于 ydsz-common-core.constant 包，因 common 重构后该常量类已迁移到各业务模块本地化。
 * 集中管理 userinfo 模块 Spring Cache 的缓存名称，避免在 {@code @Cacheable/@CacheEvict} 注解中散落字面量。
 *
 * <p>使用规范：
 * <ul>
 *   <li>所有缓存名以 {@code "ydsz:user:"} 前缀，避免与其他模块冲突</li>
 *   <li>TTL 由 Redisson Spring Cache 配置统一管理（{@code spring.cache.redis.time-to-live}）</li>
 *   <li>新增缓存名时同步追加 Javadoc 注释说明缓存内容与失效场景</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class CacheConstants {

    /**
     * 字典项缓存。
     *
     * <p>key 支持：
     * <ul>
     *   <li>{@code allTypes} — 全部字典类型列表</li>
     *   <li>{@code typeCode} — 指定 typeCode 的字典项列表</li>
     * </ul>
     */
    public static final String DICT_CACHE = "ydsz:user:dict";

    /**
     * 用户账号按 username 查询缓存（key = username）
     */
    public static final String USER_BY_USERNAME_CACHE = "ydsz:user:byUsername";

    /**
     * 用户账号按 userId 查询缓存（key = userId）
     */
    public static final String USER_BY_ID_CACHE = "ydsz:user:byId";

    private CacheConstants() {
        throw new UnsupportedOperationException("Constants class");
    }
}
