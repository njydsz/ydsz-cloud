package com.njydsz.common.auth.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * RBAC 模块配置项。
 *
 * <p>本模块将权限数据分为四类来源：
 * <ul>
 *   <li>菜单/按钮权限：role-menu-key</li>
 *   <li>接口权限：role-api-key</li>
 *   <li>行权限：role-row-key</li>
 *   <li>列权限：role-col-key</li>
 * </ul>
  *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.auth")
public class AuthProperties {
    /**
     * 是否启用 RBAC 权限校验，默认 true。
     *
     * <p>设为 false 时，所有权限校验将被跳过，适用于开发/测试环境。
     */
    private boolean enabled = true;

    /**
     * 是否启用通配符权限匹配，默认 true。
     *
     * <p>启用后，权限码支持通配符模式（如 {@code sys:user:*}），
     * 可匹配 {@code sys:user:add}、{@code sys:user:edit} 等具体权限。
     */
    private boolean wildcardEnabled = true;

    /**
     * 角色菜单/按钮权限的 Redis Key 模板。
     *
     * <p>占位符 {@code {}} 会被替换为角色编码，例如：
     * {@code ydsz-auth:role-menu:admin}
     */
    private String roleMenuKey = "ydsz-auth:role-menu:{}";

    /**
     * 角色接口权限的 Redis Key 模板。
     *
     * <p>占位符 {@code {}} 会被替换为角色编码，例如：
     * {@code ydsz-auth:role-api:admin}
     */
    private String roleApiKey = "ydsz-auth:role-api:{}";

    /**
     * 角色行级数据权限的 Redis Key 模板。
     *
     * <p>占位符 {@code {}} 会被替换为角色编码，例如：
     * {@code ydsz-auth:role-row:admin}
     */
    private String roleRowKey = "ydsz-auth:role-row:{}";

    /**
     * 角色列级权限的 Redis Key 模板。
     *
     * <p>占位符 {@code {}} 会被替换为角色编码，例如：
     * {@code ydsz-auth:role-col:admin}
     */
    private String roleColKey = "ydsz-auth:role-col:{}";

    /**
     * 忽略权限校验的角色列表，多个角色以逗号分隔。
     *
     * <p>配置在此列表中的角色将被视为超级管理员，跳过所有权限校验。
     */
    private String ignoreRoles = "";

    /**
     * 用户信息中角色编码对应的字段名，默认 {@code roleCode}。
     *
     * <p>用于从用户信息 Map 中提取角色编码，支持多角色 CSV 格式。
     */
    private String roleCodeField = "roleCode";

    /**
     * 角色菜单/按钮/接口权限本地缓存过期时间（秒），默认 30。
     */
    @Min(0)
    private Integer rolePermissionCacheSeconds = 30;

    /**
     * 权限缓存 TTL（秒），默认 30 分钟。
     */
    @Min(60)
    @Max(86400)
    private Integer permissionCacheTtlSeconds = 1800;

    /**
     * 空值缓存 TTL（秒），默认 30 秒。
     *
     * <p>当角色权限查询返回空结果（menu/button/api 全部为空）时，
     * 缓存该空值较短时间，防止缓存穿透（大量请求反复查询不存在的角色权限）。
     */
    @Min(1)
    private Integer permissionCacheNullTtlSeconds = 30;

    /**
     * 权限缓存 TTL 随机抖动百分比，默认 10（±10%）。
     *
     * <p>用于在所有缓存写入时添加随机抖动，避免大量缓存同时过期导致雪崩。
     * 例如 TTL=1800 秒、jitter=10%，实际 TTL 范围为 1620~1980 秒。
     */
    @Min(0)
    private Integer permissionCacheTtlJitterPercent = 10;

    /**
     * 权限缓存最大容量（默认 1000）。
     */
    @Min(100)
    @Max(100000)
    private Integer permissionCacheMaxSize = 1000;

    /**
     * 角色行级数据权限本地缓存过期时间（秒），默认 30。
     */
    @Min(0)
    private Integer roleDataCacheSeconds = 30;

    /**
     * 角色列级权限本地缓存过期时间（秒），默认 30。
     */
    @Min(0)
    private Integer roleColumnCacheSeconds = 30;

    /**
     * 列脱敏缓存最大条目数，默认 1000。
     *
     * <p>按角色编码缓存脱敏规则上下文，超出后按 LRU 淘汰。
     */
    @Min(1)
    private Integer desensitizeCacheMaxSize = 1000;

    /**
     * 列脱敏缓存过期时间（秒），默认 1800（30 分钟）。
     */
    @Min(1)
    private Integer desensitizeCacheTtlSeconds = 1800;

    /**
     * 本地权限降级缓存过期时间（分钟），默认 5。
     *
     * <p>当 Redis 不可用时，本地缓存作为降级兜底，此值控制其过期时间。
     */
    @Min(1)
    private Integer localPermissionCacheMinutes = 5;

    /**
     * 列权限 HMAC 签名密钥。
     *
     * <p>用于对列权限 Header（X-Visible-Columns / X-Editable-Columns）进行签名校验。
     *
     * @deprecated 自 3.0.0 起标记废弃，列权限应由服务端独立决策，无需客户端签名校验。
     */
    @Deprecated
    private String colPermissionSignKey;

    /**
     * 是否启用列权限签名校验，默认 false。
     *
     * @deprecated 自 3.0.0 起标记废弃，列权限应由服务端独立决策。
     * @since 2.0.0
     */
    @Deprecated
    private boolean colPermissionSignEnabled = false;

    /**
     * 列权限签名有效时间窗口（秒），默认 300（5 分钟）。
     *
     * @deprecated 自 3.0.0 起标记废弃，随 {@link #colPermissionSignEnabled} 一并移除。
     * @since 2.0.0
     */
    @Deprecated
    @Min(60)
    @Max(3600)
    private Integer colPermissionSignValiditySeconds = 300;

    /**
     * Redis 不可用时的权限降级策略。
     *
     * <p>当 Redis 服务不可用导致无法加载用户权限时，使用此策略决定是否放行：
     * <ul>
     *   <li>DENY（默认）：拒绝所有权限请求，返回 403</li>
     *   <li>ALLOW：放行所有权限请求（仅建议在极端容灾场景使用）</li>
     * </ul>
     */
    private String redisUnavailableFallback = "DENY";

    /**
     * 降级策略枚举。
     */
    public enum FallbackPolicy {
        /** 拒绝权限请求 */
        DENY,
        /** 放行权限请求 */
        ALLOW
    }

    /**
     * Token 黑名单配置属性
     */
    @Data
    public static class TokenBlacklistProperties {
        /**
         * 是否启用 Token 黑名单
         */
        private boolean enabled = true;
        /**
         * 黑名单过期时间（秒），应与 Token 有效期一致
         */
        @Min(1)
        private long expireSeconds = 7200;
    }

    /**
     * Token 有效期（秒），默认 7200（2 小时）。
     *
     * <p>控制 Access Token 的过期时间，过期后需使用 Refresh Token 重新获取。</p>
     */
    @Min(300)
    @Max(604800)
    private Integer tokenExpireSeconds = 7200;

    /**
     * Refresh Token 有效期（秒），默认 2592000（30 天）。
     *
     * <p>控制 Refresh Token 的过期时间，过期后需重新登录。</p>
     */
    @Min(3600)
    @Max(2592000)
    private Integer refreshTokenExpireSeconds = 2592000;

    /**
     * 空权限缓存 TTL（秒），默认 60。
     *
     * <p>当查询结果为空权限时，缓存该结果以避免缓存穿透。</p>
     *
     * @deprecated 自 3.0.0 起标记废弃，与 {@link #permissionCacheNullTtlSeconds} 重复。
     *             迁移目标：{@link #permissionCacheNullTtlSeconds}。
     */
    @Deprecated
    @Min(5)
    @Max(300)
    private Integer nullPermissionCacheTtlSeconds = 60;

    /**
     * 缓存 TTL 抖动百分比，默认 10（10%）。
     *
     * <p>在缓存过期时间上增加随机抖动，避免大量缓存同时过期导致缓存雪崩。</p>
     *
     * @deprecated 自 3.0.0 起标记废弃，与 {@link #permissionCacheTtlJitterPercent} 重复。
     *             迁移目标：{@link #permissionCacheTtlJitterPercent}。
     */
    @Deprecated
    @Min(0)
    @Max(50)
    private Integer cacheTtlJitterPercent = 10;

    /**
     * 最大登录失败次数，默认 5。
     *
     * <p>超过此次数后账户将被锁定。</p>
     *
     * @deprecated 自 3.0.0 起标记废弃，暴力破解防护应由网关层或独立安全模块负责。
     */
    @Deprecated
    @Min(3)
    @Max(20)
    private Integer maxFailedAttempts = 5;

    /**
     * 账户锁定时间（分钟），默认 30。
     *
     * <p>达到最大失败次数后，账户被锁定的持续时间。</p>
     *
     * @deprecated 自 3.0.0 起标记废弃，暴力破解防护应由网关层或独立安全模块负责。
     */
    @Deprecated
    @Min(1)
    @Max(1440)
    private Integer lockTimeMinutes = 30;

    /**
     * 是否启用权限预热（默认 true）。
     *
     * <p>启用后，应用启动时会自动加载指定角色的权限到本地缓存，
     * 减少首次请求的延迟。</p>
     *
     * @deprecated 自 3.0.0 起标记废弃，权限预热为性能优化手段，非核心功能。
     */
    @Deprecated
    private Boolean warmUpEnabled = true;

    /**
     * 需要预热的角色 ID 列表。
     *
     * <p>建议配置系统中高频使用的角色，如 admin、operator 等。</p>
     *
     * @deprecated 自 3.0.0 起标记废弃，随 {@link #warmUpEnabled} 一并移除。
     */
    @Deprecated
    private List<String> warmUpRoleIds = new ArrayList<>();

    /**
     * 预热延迟时间（毫秒），默认 3000。
     *
     * <p>延迟执行预热，避免与应用启动竞争资源。</p>
     *
     * @deprecated 自 3.0.0 起标记废弃，随 {@link #warmUpEnabled} 一并移除。
     */
    @Deprecated
    private Long warmUpDelay = 3000L;

    /**
     * Token 黑名单配置
     */
    private TokenBlacklistProperties blacklist = new TokenBlacklistProperties();

    /**
     * 多端会话控制配置。
     *
     * @deprecated 自 3.0.0 起标记废弃，会话管理已超出本模块职责边界。
     *             迁移目标：独立会话管理模块或网关层。
     */
    @Deprecated
    private SessionProperties session = new SessionProperties();

    /**
     * 多端会话控制配置属性。
     *
     * @deprecated 自 3.0.0 起标记废弃，随 {@link AuthProperties#session} 一并移除。
     */
    @Deprecated
    @Data
    public static class SessionProperties {
        /**
         * 启用会话控制，默认 false。
         *
         * <p>启用后会在 Redis 中维护用户的会话列表，超出 max-sessions-per-user 时淘汰会话。</p>
         */
        private boolean enabled = false;

        /**
         * 单用户最大并发会话数，默认 5。
         *
         * <p>超出此数量时按 eviction-strategy 淘汰。</p>
         */
        @Min(1)
        private int maxSessionsPerUser = 5;

        /**
         * 超出限制时的会话淘汰策略。
         *
         * <p>支持的值：</p>
         * <ul>
         *   <li>FIFO（默认）：淘汰最早注册的会话</li>
         *   <li>LRU：淘汰最近最少使用的会话（依赖会话访问时间戳）</li>
         * </ul>
         */
        private EvictionStrategy evictionStrategy = EvictionStrategy.FIFO;
    }

    /**
     * 会话淘汰策略枚举。
     */
    public enum EvictionStrategy {
        /** 先进先出（淘汰最早注册的会话） */
        FIFO,
        /** 最近最少使用（淘汰最久未被访问的会话） */
        LRU
    }

    /**
     * 获取降级策略枚举值。
     *
     * @return 降级策略
     */
    public FallbackPolicy getFallbackPolicy() {
        if (redisUnavailableFallback == null || redisUnavailableFallback.trim().isEmpty()) {
            return FallbackPolicy.DENY;
        }
        try {
            return FallbackPolicy.valueOf(redisUnavailableFallback.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FallbackPolicy.DENY;
        }
    }
}
