package com.njydsz.common.auth.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RBAC 模块配置项。
 *
 * <p>本模块将权限数据分为四类来源：
 *
 * <ul>
 *   <li>菜单/按钮权限：role-menu-key
 *   <li>接口权限：role-api-key
 *   <li>行权限：role-row-key
 *   <li>列权限：role-col-key
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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
   * <p>启用后，权限码支持通配符模式（如 {@code sys:user:*}）， 可匹配 {@code sys:user:add}、{@code sys:user:edit} 等具体权限。
   */
  private boolean wildcardEnabled = true;

  /**
   * 角色菜单/按钮权限的 Redis Key 模板。
   *
   * <p>占位符 {@code {}} 会被替换为角色编码，例如： {@code ydsz-auth:role-menu:admin}
   */
  private String roleMenuKey = "ydsz-auth:role-menu:{}";

  /**
   * 角色接口权限的 Redis Key 模板。
   *
   * <p>占位符 {@code {}} 会被替换为角色编码，例如： {@code ydsz-auth:role-api:admin}
   */
  private String roleApiKey = "ydsz-auth:role-api:{}";

  /**
   * 角色行级数据权限的 Redis Key 模板。
   *
   * <p>占位符 {@code {}} 会被替换为角色编码，例如： {@code ydsz-auth:role-row:admin}
   */
  private String roleRowKey = "ydsz-auth:role-row:{}";

  /**
   * 角色列级权限的 Redis Key 模板。
   *
   * <p>占位符 {@code {}} 会被替换为角色编码，例如： {@code ydsz-auth:role-col:admin}
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

  /** 角色菜单/按钮/接口权限本地缓存过期时间（秒），默认 30。 */
  @Min(0)
  private Integer rolePermissionCacheSeconds = 30;

  /** 权限缓存 TTL（秒），默认 30 分钟。 */
  @Min(60)
  @Max(86400)
  private Integer permissionCacheTtlSeconds = 1800;

  /**
   * 空值缓存 TTL（秒），默认 30 秒。
   *
   * <p>当角色权限查询返回空结果（menu/button/api 全部为空）时， 缓存该空值较短时间，防止缓存穿透（大量请求反复查询不存在的角色权限）。
   */
  @Min(1)
  private Integer permissionCacheNullTtlSeconds = 30;

  /**
   * 权限缓存 TTL 随机抖动百分比，默认 10（±10%）。
   *
   * <p>用于在所有缓存写入时添加随机抖动，避免大量缓存同时过期导致雪崩。 例如 TTL=1800 秒、jitter=10%，实际 TTL 范围为 1620~1980 秒。
   */
  @Min(0)
  private Integer permissionCacheTtlJitterPercent = 10;

  /** 权限缓存最大容量（默认 1000）。 */
  @Min(100)
  @Max(100000)
  private Integer permissionCacheMaxSize = 1000;

  /** 角色行级数据权限本地缓存过期时间（秒），默认 30。 */
  @Min(0)
  private Integer roleDataCacheSeconds = 30;

  /** 角色列级权限本地缓存过期时间（秒），默认 30。 */
  @Min(0)
  private Integer roleColumnCacheSeconds = 30;

  /**
   * 列脱敏缓存最大条目数，默认 1000。
   *
   * <p>按角色编码缓存脱敏规则上下文，超出后按 LRU 淘汰。
   */
  @Min(1)
  private Integer desensitizeCacheMaxSize = 1000;

  /** 列脱敏缓存过期时间（秒），默认 1800（30 分钟）。 */
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
   * Redis 不可用时的权限降级策略。
   *
   * <p>当 Redis 服务不可用导致无法加载用户权限时，使用此策略决定是否放行：
   *
   * <ul>
   *   <li>DENY（默认）：拒绝所有权限请求，返回 403
   *   <li>ALLOW：放行所有权限请求（仅建议在极端容灾场景使用）
   * </ul>
   */
  private String redisUnavailableFallback = "DENY";

  /**
   * 是否启用跨实例权限缓存失效 Pub/Sub 总线（默认关闭）。
   *
   * <p>单实例场景无需启用，避免不必要的 Redis 订阅开销。
   * 集群部署时启用，确保权限变更即时同步到所有节点的本地缓存。
   */
  private boolean crossInstanceEnabled = false;

  /** 降级策略枚举。 */
  public enum FallbackPolicy {
    /** 拒绝权限请求 */
    DENY,
    /** 放行权限请求 */
    ALLOW
  }

  /** Token 黑名单配置属性 */
  @Data
  public static class TokenBlacklistProperties {
    /** 是否启用 Token 黑名单 */
    private boolean enabled = true;

    /** 黑名单过期时间（秒），应与 Token 有效期一致 */
    @Min(1)
    private long expireSeconds = 7200;
  }

  /** Bloom Filter 配置属性。 */
  @Data
  @Validated
  public static class BloomFilterProperties {
    /**
     * Bloom Filter 预计插入元素数，默认 100 000。
    
     * <p>应根据业务规模调整：黑名单峰值容量（如并发登出峰值 × access_token TTL 内的活跃度）。 设置过小导致误判率升高（退化为每次仍需查 Redis）；设置过大浪费内存。
     */
    @Min(1000)
    private long expectedInsertions = 100_000L;

    /**
     * Bloom Filter 目标误判率，默认 0.001（0.1%）。
        
     * <p>误判率越低，位数组越大、哈希函数越多（内存与计算成本越高）。 0.1% 表示每 1000 次 Redis 查询可短路 999 次。
     */
    @DecimalMin("0.0001")
    @DecimalMax("0.1")
    private double falsePositiveRate = 0.001;
  }

  /**
   * Token 有效期（秒），默认 7200（2 小时）。
   *
   * <p>控制 Access Token 的过期时间，过期后需使用 Refresh Token 重新获取。
   */
  @Min(300)
  @Max(604800)
  private Integer tokenExpireSeconds = 7200;

  /**
   * Refresh Token 有效期（秒），默认 2592000（30 天）。
   *
   * <p>控制 Refresh Token 的过期时间，过期后需重新登录。
   */
  @Min(3600)
  @Max(2592000)
  private Integer refreshTokenExpireSeconds = 2592000;

  /** Token 黑名单配置 */
  private TokenBlacklistProperties blacklist = new TokenBlacklistProperties();

  /** Bloom Filter 配置 */
  private BloomFilterProperties bloomFilter = new BloomFilterProperties();

  /** 登录防护配置 */
  private LoginDefenseProperties loginDefense = new LoginDefenseProperties();

  /** 会话管理配置 */
  private SessionProperties session = new SessionProperties();

  /** 登录防护配置属性 */
  @Data
  public static class LoginDefenseProperties {
    /** 是否启用登录防护 */
    private boolean enabled = true;

    /** 最大连续失败次数（达到后锁定账号），默认 5 */
    @Min(1)
    @Max(100)
    private int maxFailAttempts = 5;

    /** 账号锁定时间（分钟），默认 15 */
    @Min(1)
    @Max(1440)
    private int lockoutMinutes = 15;

    /** IP 级限流：每窗口最大尝试次数，默认 100 */
    @Min(1)
    private int ipRateLimit = 100;

    /** IP 级限流窗口（秒），默认 60 */
    @Min(10)
    private int ipRateWindowSeconds = 60;

    /**
     * 连续失败次数达到此阈值时强制验证码校验，默认 3。
     * <p>值 0 表示禁用验证码策略。
     */
    @Min(0)
    private int captchaThreshold = 3;

    /** 失败计数过期时间（秒），默认 30 分钟 */
    @Min(60)
    private int failCountExpireSeconds = 1800;
  }

  /** 会话管理配置属性 */
  @Data
  public static class SessionProperties {
    /** 是否启用会话注册表 */
    private boolean enabled = false;

    /** 同账号最大并发会话数，默认 5 */
    @Min(1)
    @Max(50)
    private int maxConcurrentSessions = 5;

    /**
     * 超出最大并发时会话踢出策略。
     * <p>OLDEST = 踢出最早的会话；NEWEST = 拒绝最新请求。
     */
    private String kickoutPolicy = "OLDEST";

    /** 会话心跳保活间隔（毫秒），默认 30000 */
    @Min(5000)
    private long heartbeatIntervalMillis = 30000;

    /** 踢出策略枚举。 */
    public enum KickoutPolicy {
      /** 踢出最早会话 */
      OLDEST,
      /** 拒绝最新请求 */
      NEWEST
    }

    /**
     * 获取踢出策略枚举值。
     *
     * @return 踢出策略，解析失败时返回 OLDEST
     */
    public KickoutPolicy getKickoutPolicyEnum() {
      if (kickoutPolicy == null || kickoutPolicy.isBlank()) {
        return KickoutPolicy.OLDEST;
      }
      try {
        return KickoutPolicy.valueOf(kickoutPolicy.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return KickoutPolicy.OLDEST;
      }
    }
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
