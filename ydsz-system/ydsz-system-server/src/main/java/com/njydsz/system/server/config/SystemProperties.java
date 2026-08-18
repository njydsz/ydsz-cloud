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
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.system")
public class SystemProperties {

  /** 默认配置缓存 TTL（分钟） */
  private static final int DEFAULT_CACHE_TTL_MINUTES = 5;

  /** 默认字典缓存 TTL（分钟） */
  private static final int DEFAULT_DICT_CACHE_TTL_MINUTES = 10;

  /** 是否启用系统模块健康检查（影响 {@code /actuator/health} 是否暴露 system 详情） */
  private boolean healthEnabled = true;

  /** 配置缓存配置（{@code ydsz.system.config.*}） */
  private ConfigCache config = new ConfigCache();

  /** 字典缓存配置（{@code ydsz.system.dict.*}） */
  private DictCache dict = new DictCache();

  /** 系统变量缓存配置（{@code ydsz.system.variable.*}） */
  private VariableCache variable = new VariableCache();

  /** 应用密钥配置（{@code ydsz.system.app.*}） */
  private App app = new App();

  /** 系统版本号（用于前端展示，对应 ydsz.system.version） */
  private String version = "1.0.0";

  /** 配置缓存配置。 */
  @Data
  public static class ConfigCache {
    /** 是否启用配置缓存（false 时所有 {@code ydsz_config} 走 DB） */
    private boolean enabled = true;

    /**
     * 配置缓存 TTL（分钟），影响 {@code ConfigServiceImpl.getConfigValue/getConfigsByGroup/listPublicConfigs}
     */
    private int cacheTtlMinutes = DEFAULT_CACHE_TTL_MINUTES;

    /**
     * 配置值格式严格校验开关（P1-6）：true 时值格式非法将阻止保存，false 时仅告警放行（向后兼容存量非法值）。
     */
    private boolean strictValidation = false;
  }

  /** 字典缓存配置。 */
  @Data
  public static class DictCache {
    /** 是否启用字典缓存（false 时所有 {@code ydsz_dict_item} 走 DB） */
    private boolean enabled = true;

    /** 字典缓存 TTL（分钟），影响 {@code DictItemServiceImpl} 所有缓存命中路径 */
    private int cacheTtlMinutes = DEFAULT_DICT_CACHE_TTL_MINUTES;
  }

  /** 系统变量缓存配置。 */
  @Data
  public static class VariableCache {
    /** 是否启用系统变量缓存。 */
    private boolean enabled = true;

    /** 系统变量缓存 TTL（分钟）。 */
    private int cacheTtlMinutes = DEFAULT_CACHE_TTL_MINUTES;
  }

  /** 应用密钥配置。 */
  @Data
  public static class App {
    /** BCrypt 加密强度（4-31）。 */
    private int bcryptStrength = 10;
  }
}
