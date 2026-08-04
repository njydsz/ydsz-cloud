package com.remisoft.common.cache.spring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import com.remisoft.common.cache.builder.CacheType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * YdszCache Spring Boot 配置属性
 *
 * <p>配置前缀：{@code remi.cache}
 *
 * <p>配置示例（全局默认 + per-cache 覆盖）：
 *
 * <pre>
 * remi:
 *   cache:
 *     type: TINYLFU
 *     maximum-size: 1000
 *     expire-after-write: 30
 *     expire-time-unit: MINUTES
 *     allow-null-values: true
 *     # per-cache 配置（覆盖全局默认）
 *     caches:
 *       users:
 *         type: LRU
 *         maximum-size: 5000
 *         expire-after-write: 60
 *       orders:
 *         type: TINYLFU
 *         maximum-size: 20000
 *         expire-after-write: 10
 *       config:
 *         type: CONCURRENT
 *         maximum-size: 100
 *         expire-after-write: 0
 * </pre>
 *
 *
 * @author remi-team
 * @since 1.0.0
 * 
 */
@ConfigurationProperties(prefix = "remi.cache")
@Validated
public class YdszCacheProperties {

  @NotNull
  private CacheType type = CacheType.TINYLFU;

  private List<String> cacheNames = new ArrayList<>();

  @Min(1)
  private long maximumSize = 1000;

  @Min(0)
  private long expireAfterWrite = 30;

  @NotNull
  private TimeUnit expireTimeUnit = TimeUnit.MINUTES;

  private boolean allowNullValues = true;

  @Min(1)
  private int initialCapacity = 64;

  /** 访问后过期时间 */
  private long expireAfterAccess = 0;

  /** 刷新间隔 */
  private long refreshAfterWrite = 0;

  /** 是否启用统计 */
  private boolean recordStats = true;

  /** 是否使用弱引用键 */
  private boolean weakKeys = false;

  /** 是否使用弱引用值 */
  private boolean weakValues = false;

  /** 是否使用软引用值 */
  private boolean softValues = false;

  /**
   * per-cache 配置映射
   *
   * <p>key 为缓存名称，value 为该缓存的独立配置（覆盖全局默认值）
   */
  private Map<String, CacheConfig> caches = new LinkedHashMap<>();

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
    private Boolean weakKeys;
    private Boolean weakValues;
    private Boolean softValues;

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

    public Boolean getWeakKeys() {
      return weakKeys;
    }

    public void setWeakKeys(Boolean weakKeys) {
      this.weakKeys = weakKeys;
    }

    public Boolean getWeakValues() {
      return weakValues;
    }

    public void setWeakValues(Boolean weakValues) {
      this.weakValues = weakValues;
    }

    public Boolean getSoftValues() {
      return softValues;
    }

    public void setSoftValues(Boolean softValues) {
      this.softValues = softValues;
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

  public boolean isWeakKeys() {
    return weakKeys;
  }

  public void setWeakKeys(boolean weakKeys) {
    this.weakKeys = weakKeys;
  }

  public boolean isWeakValues() {
    return weakValues;
  }

  public void setWeakValues(boolean weakValues) {
    this.weakValues = weakValues;
  }

  public boolean isSoftValues() {
    return softValues;
  }

  public void setSoftValues(boolean softValues) {
    this.softValues = softValues;
  }

  public Map<String, CacheConfig> getCaches() {
    return caches;
  }

  public void setCaches(Map<String, CacheConfig> caches) {
    this.caches = caches;
  }
}
