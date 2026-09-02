package com.njydsz.common.cache.spring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.njydsz.common.cache.builder.CacheType;

/**
 * YdszCache Spring Boot 配置属性
 *
 * <p>配置前缀：{@code ydsz.cache}
 *
 * <p>配置示例（全局默认 + per-cache 覆盖）：
 *
 * <pre>
 * ydsz:
 *   cache:
 *     type: TINYLFU
 *     maximum-size: 1000
 *     expire-after-write: 30
 *     expire-time-unit: MINUTES
 *     allow-null-values: true
 *     # 空值占位 TTL（毫秒，防穿透短 TTL，0 表示禁用走 NullValue + 主 TTL）
 *     null-value-ttl-min: 30000
 *     null-value-ttl-max: 60000
 *     # per-cache 配置（覆盖全局默认）
 *     caches:
 *       users:
 *         type: TINYLFU
 *         maximum-size: 5000
 *         expire-after-write: 60
 *         null-value-ttl-min: 5000
 *         null-value-ttl-max: 10000
 *       orders:
 *         type: STRIPED
 *         maximum-size: 20000
 *         expire-after-write: 10
 *       config:
 *         type: TINYLFU
 *         maximum-size: 100
 *         expire-after-write: 0
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.cache")
@Validated
public class YdszCacheProperties {

  @NotNull private CacheType type = CacheType.TINYLFU;

  private List<String> cacheNames = new ArrayList<>(4);

  @Min(1)
  private long maximumSize = 1000;

  @Min(0)
  private long expireAfterWrite = 30;

  @NotNull private TimeUnit expireTimeUnit = TimeUnit.MINUTES;

  private boolean allowNullValues = true;

  @Min(1)
  private int initialCapacity = 64;

  /** 访问后过期时间 */
  private long expireAfterAccess = 0;

  /** 刷新间隔 */
  private long refreshAfterWrite = 0;

  /** 是否启用统计 */
  private boolean recordStats = true;

  /**
   * 空值占位 TTL 下界（毫秒）。
   *
   * <p>大于 0 时启用注解路径空值短 TTL（防穿透）：valueLoader 返回 null 后注册带随机抖动的短 TTL 占位，
   * 占位期内不回源，过期自动恢复。0 表示禁用，走 NullValue 包装 + 主 TTL 的旧行为。
   */
  private long nullValueTtlMin = 0;

  /** 空值占位 TTL 上界（毫秒），实际过期时间在 [min, max] 区间内随机抖动以防雪崩 */
  private long nullValueTtlMax = 0;

  /**
   * per-cache 配置映射
   *
   * <p>key 为缓存名称，value 为该缓存的独立配置（覆盖全局默认值）
   */
  private Map<String, CacheConfig> caches = new LinkedHashMap<>(16);

  /** Per-cache 配置类 */
  public static class CacheConfig {
    private CacheType type;
    private Long maximumSize;
    private Long expireAfterWrite;
    private TimeUnit expireTimeUnit;
    private Integer initialCapacity;
    private Long expireAfterAccess;
    private Long refreshAfterWrite;
    private Boolean recordStats;
    /** 空值占位 TTL 下界（毫秒），覆盖全局 nullValueTtlMin */
    private Long nullValueTtlMin;
    /** 空值占位 TTL 上界（毫秒），覆盖全局 nullValueTtlMax */
    private Long nullValueTtlMax;

    public CacheType getType() {
      return type;
    }

    public void setType(CacheType type) {
      this.type = type;
    }

    public Long getMaximumSize() {
      return maximumSize;
    }

    public void setMaximumSize(Long maximumSize) {
      this.maximumSize = maximumSize;
    }

    public Long getExpireAfterWrite() {
      return expireAfterWrite;
    }

    public void setExpireAfterWrite(Long expireAfterWrite) {
      this.expireAfterWrite = expireAfterWrite;
    }

    public TimeUnit getExpireTimeUnit() {
      return expireTimeUnit;
    }

    public void setExpireTimeUnit(TimeUnit expireTimeUnit) {
      this.expireTimeUnit = expireTimeUnit;
    }

    public Integer getInitialCapacity() {
      return initialCapacity;
    }

    public void setInitialCapacity(Integer initialCapacity) {
      this.initialCapacity = initialCapacity;
    }

    public Long getExpireAfterAccess() {
      return expireAfterAccess;
    }

    public void setExpireAfterAccess(Long expireAfterAccess) {
      this.expireAfterAccess = expireAfterAccess;
    }

    public Long getRefreshAfterWrite() {
      return refreshAfterWrite;
    }

    public void setRefreshAfterWrite(Long refreshAfterWrite) {
      this.refreshAfterWrite = refreshAfterWrite;
    }

    public Boolean getRecordStats() {
      return recordStats;
    }

    public void setRecordStats(Boolean recordStats) {
      this.recordStats = recordStats;
    }

    public Long getNullValueTtlMin() {
      return nullValueTtlMin;
    }

    public void setNullValueTtlMin(Long nullValueTtlMin) {
      this.nullValueTtlMin = nullValueTtlMin;
    }

    public Long getNullValueTtlMax() {
      return nullValueTtlMax;
    }

    public void setNullValueTtlMax(Long nullValueTtlMax) {
      this.nullValueTtlMax = nullValueTtlMax;
    }
  }

  public CacheType getType() {
    return type;
  }

  public void setType(CacheType type) {
    this.type = type;
  }

  public List<String> getCacheNames() {
    return cacheNames;
  }

  public void setCacheNames(List<String> cacheNames) {
    this.cacheNames = cacheNames;
  }

  public long getMaximumSize() {
    return maximumSize;
  }

  public void setMaximumSize(long maximumSize) {
    this.maximumSize = maximumSize;
  }

  public long getExpireAfterWrite() {
    return expireAfterWrite;
  }

  public void setExpireAfterWrite(long expireAfterWrite) {
    this.expireAfterWrite = expireAfterWrite;
  }

  public TimeUnit getExpireTimeUnit() {
    return expireTimeUnit;
  }

  public void setExpireTimeUnit(TimeUnit expireTimeUnit) {
    this.expireTimeUnit = expireTimeUnit;
  }

  public boolean isAllowNullValues() {
    return allowNullValues;
  }

  public void setAllowNullValues(boolean allowNullValues) {
    this.allowNullValues = allowNullValues;
  }

  public int getInitialCapacity() {
    return initialCapacity;
  }

  public void setInitialCapacity(int initialCapacity) {
    this.initialCapacity = initialCapacity;
  }

  public long getExpireAfterAccess() {
    return expireAfterAccess;
  }

  public void setExpireAfterAccess(long expireAfterAccess) {
    this.expireAfterAccess = expireAfterAccess;
  }

  public long getRefreshAfterWrite() {
    return refreshAfterWrite;
  }

  public void setRefreshAfterWrite(long refreshAfterWrite) {
    this.refreshAfterWrite = refreshAfterWrite;
  }

  public boolean isRecordStats() {
    return recordStats;
  }

  public void setRecordStats(boolean recordStats) {
    this.recordStats = recordStats;
  }

  public long getNullValueTtlMin() {
    return nullValueTtlMin;
  }

  public void setNullValueTtlMin(long nullValueTtlMin) {
    this.nullValueTtlMin = nullValueTtlMin;
  }

  public long getNullValueTtlMax() {
    return nullValueTtlMax;
  }

  public void setNullValueTtlMax(long nullValueTtlMax) {
    this.nullValueTtlMax = nullValueTtlMax;
  }

  public Map<String, CacheConfig> getCaches() {
    return caches;
  }

  public void setCaches(Map<String, CacheConfig> caches) {
    this.caches = caches;
  }
}
