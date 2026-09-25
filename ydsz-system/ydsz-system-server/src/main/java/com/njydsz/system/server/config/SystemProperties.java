package com.njydsz.system.server.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 系统模块配置属性
 *
 * <p>对应配置前缀 {@code ydsz.system}，所有配置项均可通过 Nacos 动态调整。 修改后无需重启服务，{@code @ConfigurationProperties}
 * 自动热加载。
 *
 * <p><b>配置项总览：</b>
 *
 * <ul>
 *   <li>{@code ydsz.system.health-enabled}：是否启用系统模块健康检查（默认 true）
 *   <li>{@code ydsz.system.config.cache-ttl-minutes}：配置缓存 TTL（默认 5）
 *   <li>{@code ydsz.system.dict.cache-ttl-minutes}：字典缓存 TTL（默认 10）
 *   <li>{@code ydsz.system.variable.cache-ttl-minutes}：变量缓存 TTL（默认 5）
 *   <li>{@code ydsz.system.app.bcrypt-strength}：应用密钥 BCrypt 加密强度（默认 10）
 *   <li>内部 API IP 白名单已迁移至 {@code ydsz.safe.ip-access.*}（common-safe 统一管控，支持 CIDR），
 *       不再由本配置类持有
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.system")
public class SystemProperties {

  /** 默认配置缓存 TTL（分钟） */
  private static final int DEFAULT_CACHE_TTL_MINUTES = 15;

  /** 默认字典缓存 TTL（分钟） */
  private static final int DEFAULT_DICT_CACHE_TTL_MINUTES = 30;

  /** 是否启用系统模块健康检查（影响 {@code /actuator/health} 是否暴露 system 详情） */
  private boolean isHealthEnabled = true;

  /** 配置缓存配置（{@code ydsz.system.config.*}） */
  private ConfigCache config = new ConfigCache();

  /** 字典缓存配置（{@code ydsz.system.dict.*}） */
  private DictCache dict = new DictCache();

  /** 系统变量缓存配置（{@code ydsz.system.variable.*}） */
  private VariableCache variable = new VariableCache();

  /** 应用密钥配置（{@code ydsz.system.app.*}） */
  private App app = new App();

  /** 缓存配置（{@code ydsz.system.cache.*}） */
  private Cache cache = new Cache();

  /** 系统版本号（用于前端展示，对应 ydsz.system.version） */
  private String version = "26.09.01";

  /** 配置缓存配置。 */
  @Data
  public static class ConfigCache {
    /** 是否启用配置缓存（false 时所有 {@code ydsz_sys_config} 走 DB） */
    private boolean isEnabled = true;

    /**
     * 配置缓存 TTL（分钟），影响 {@code ConfigServiceImpl.getConfigValue/getConfigsByGroup/listPublicConfigs}
     */
    private int cacheTtlMinutes = DEFAULT_CACHE_TTL_MINUTES;

    /**
     * 配置值格式严格校验开关（P1-6）：true 时值格式非法将阻止保存，false 时仅告警放行（向后兼容存量非法值）。
     */
    private boolean isStrictValidation = false;
  }

  /** 字典缓存配置。 */
  @Data
  public static class DictCache {
    /** 是否启用字典缓存（false 时所有 {@code ydsz_sys_dict_item} 走 DB） */
    private boolean isEnabled = true;

    /** 字典缓存 TTL（分钟），影响 {@code DictItemServiceImpl} 所有缓存命中路径 */
    private int cacheTtlMinutes = DEFAULT_DICT_CACHE_TTL_MINUTES;
  }

  /** 系统变量缓存配置。 */
  @Data
  public static class VariableCache {
    /** 是否启用系统变量缓存。 */
    private boolean isEnabled = true;

    /** 系统变量缓存 TTL（分钟）。 */
    private int cacheTtlMinutes = DEFAULT_CACHE_TTL_MINUTES;
  }

  /** 应用密钥配置。 */
  @Data
  public static class App {
    /** 默认应用密钥校验缓存 TTL（秒）：5 分钟 */
    private static final long DEFAULT_VALIDATE_CACHE_TTL_SECONDS = 5L * 60L;

    /** 默认连续失败锁定阈值（次） */
    private static final int DEFAULT_MAX_FAIL_COUNT = 5;

    /** 默认失败锁定 TTL（秒）：30 分钟 */
    private static final long DEFAULT_FAIL_LOCK_TTL_SECONDS = 30L * 60L;

    /** BCrypt 加密强度（4-31）。 */
    private int bcryptStrength = 10;

    /** 应用密钥校验缓存 TTL（秒），校验成功后缓存结果跳过 BCrypt 校验。 */
    private long validateCacheTtlSeconds = DEFAULT_VALIDATE_CACHE_TTL_SECONDS;

    /** 连续失败锁定阈值（次），达到该次数后在锁定时间内拒绝所有校验请求。 */
    private int maxFailCount = DEFAULT_MAX_FAIL_COUNT;

    /** 失败锁定 TTL（秒），期间所有校验请求直接拒绝。 */
    private long failLockTtlSeconds = DEFAULT_FAIL_LOCK_TTL_SECONDS;
  }

  /** 缓存配置。 */
  @Data
  public static class Cache {
    /**
     * 是否启用跨实例缓存失效（Redis Pub/Sub）。
     *
     * <p>默认 false（单实例部署或接受最终一致性场景）。多实例部署且需实时一致性时开启。
     */
    private boolean isCrossInstanceEnabled = false;

    /**
     * 是否启用缓存一致性兜底刷新（周期性全量刷新本地缓存）。
     *
     * <p>默认 false。开启后每隔 {@code consistencyRefreshIntervalMs} 从 DB 全量刷新本地缓存，
     * 提供最终一致性兜底。建议仅在 {@code crossInstanceEnabled=false} 的多实例部署场景开启。
     */
    private boolean isConsistencyRefreshEnabled = false;

    /** 缓存一致性兜底刷新间隔（毫秒），默认 5 分钟（300000ms）。 */
    private long consistencyRefreshIntervalMs = 300000L;

    /** 缓存一致性兜底刷新首次执行延迟（毫秒），默认 2 分钟（120000ms）。 */
    private long consistencyRefreshInitialDelayMs = 120000L;
  }

  /** 二次认证配置（{@code ydsz.system.secondary-auth.*}） */
  private SecondaryAuth secondaryAuth = new SecondaryAuth();

  /** 二次认证配置属性。 */
  @Data
  public static class SecondaryAuth {
    /** 默认二次认证令牌有效期（分钟）：30 分钟 */
    private static final int DEFAULT_TOKEN_TTL_MINUTES = 30;

    /** 默认连续验证失败锁定阈值（次）：5 次 */
    private static final int DEFAULT_MAX_FAIL_COUNT = 5;

    /** 默认验证失败锁定时间（分钟）：15 分钟 */
    private static final int DEFAULT_FAIL_LOCK_MINUTES = 15;

    /** 二次认证令牌有效期（分钟），过期后需重新验证密码。 */
    private int tokenTtlMinutes = DEFAULT_TOKEN_TTL_MINUTES;

    /** 连续验证失败锁定阈值（次），达到后暂时禁止二次认证。 */
    private int maxFailCount = DEFAULT_MAX_FAIL_COUNT;

    /** 验证失败锁定时间（分钟），期间拒绝所有二次认证请求。 */
    private int failLockMinutes = DEFAULT_FAIL_LOCK_MINUTES;
  }
}
