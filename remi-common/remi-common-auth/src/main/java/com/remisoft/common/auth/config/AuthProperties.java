package com.remisoft.common.auth.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
 * @author remi-team
 * @since 1.0.0
 * 
 */
@Data
@ConfigurationProperties(prefix = "remi.auth")
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
     * {@code remi-auth:role-menu:admin}
     */
    private String roleMenuKey = "remi-auth:role-menu:{}";

    /**
     * 角色接口权限的 Redis Key 模板。
     *
     * <p>占位符 {@code {}} 会被替换为角色编码，例如：
     * {@code remi-auth:role-api:admin}
     */
    private String roleApiKey = "remi-auth:role-api:{}";

    /**
     * 角色行级数据权限的 Redis Key 模板。
     *
     * <p>占位符 {@code {}} 会被替换为角色编码，例如：
     * {@code remi-auth:role-row:admin}
     */
    private String roleRowKey = "remi-auth:role-row:{}";

    /**
     * 角色列级权限的 Redis Key 模板。
     *
     * <p>占位符 {@code {}} 会被替换为角色编码，例如：
     * {@code remi-auth:role-col:admin}
     */
    private String roleColKey = "remi-auth:role-col:{}";

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
    @Min(1)
    private Integer permissionCacheTtlSeconds = 1800;

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
     * <p>用于对列权限 Header（X-Visible-Columns / X-Editable-Columns）进行签名校验，
     * 防止客户端伪造或篡改列权限数据。
     *
     * <p>为空时跳过签名校验（仅建议开发/测试环境使用）。
     */
    private String colPermissionSignKey;

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
     * 是否启用权限预热（默认 true）。
     *
     * <p>启用后，应用启动时会自动加载指定角色的权限到本地缓存，
     * 减少首次请求的延迟。
     */
    private Boolean warmUpEnabled = true;

    /**
     * 需要预热的角色 ID 列表。
     *
     * <p>建议配置系统中高频使用的角色，如 admin、operator 等。
     */
    private List<String> warmUpRoleIds = new ArrayList<>();

    /**
     * 预热延迟时间（毫秒），默认 3000。
     *
     * <p>延迟执行预热，避免与应用启动竞争资源。
     */
    private Long warmUpDelay = 3000L;

    /**
     * Token 黑名单配置
     */
    private TokenBlacklistProperties blacklist = new TokenBlacklistProperties();

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
