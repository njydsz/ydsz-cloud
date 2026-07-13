package com.njydsz.pmis.common.cache.spring;

import com.njydsz.pmis.common.cache.builder.CacheType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * YdszCache Spring Boot 配置属性
 *
 * <p>配置前缀：{@code ydsz.cache}
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   cache:
 *     type: TINYLFU
 *     cache-names: users,orders,config
 *     maximum-size: 1000
 *     expire-after-write: 30
 *     expire-time-unit: MINUTES
 *     allow-null-values: true
 * </pre>
 *
 * @author Marvin Lee
 * @version 3.5.0
 */
@ConfigurationProperties(prefix = "ydsz.cache")
public class YdszCacheProperties {

    private CacheType type = CacheType.TINYLFU;

    private List<String> cacheNames = new ArrayList<>();

    private long maximumSize = 1000;

    private long expireAfterWrite = 30;

    private TimeUnit expireTimeUnit = TimeUnit.MINUTES;

    private boolean allowNullValues = true;

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
}
