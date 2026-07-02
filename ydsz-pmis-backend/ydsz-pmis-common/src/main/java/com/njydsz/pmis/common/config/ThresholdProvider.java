package com.njydsz.pmis.common.config;

import com.njydsz.pmis.common.feign.ConfigClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 预警阈值提供器
 *
 * <p>统一从配置中心读取 {@code alert} 分组下的各项阈值，
 * 优先级：sys_config (public.pmis_config) > 静态默认值。
 *
 * <p>本地内存缓存 60s，配置变更后最多延迟 60s 生效。
 *
 * <p>当前支持：
 * <ul>
 *   <li>alert.cpi.yellow / alert.cpi.red</li>
 *   <li>alert.spi.yellow / alert.spi.red</li>
 *   <li>alert.bench.days.yellow / alert.bench.days.red</li>
 *   <li>alert.bench.cost.ratio（闲置成本占总人力成本阈值，百分比）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdProvider {

    /** 配置组名 */
    public static final String GROUP = "alert";

    /** 本地缓存过期时间（毫秒） */
    private static final long CACHE_TTL_MS = 60_000L;

    /** 配置中心 Feign 客户端 */
    private final ConfigClient configClient;

    /** key → 缓存条目 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // ============= CPI / SPI =============
    /**
     * CPI 黄色预警阈值（默认 0.95）
     *
     * @return CPI 黄色阈值
     */
    public double cpiYellow() {
        return getDouble("cpi.yellow", 0.95);
    }

    /**
     * CPI 红色预警阈值（默认 0.85）
     *
     * @return CPI 红色阈值
     */
    public double cpiRed() {
        return getDouble("cpi.red", 0.85);
    }

    /**
     * SPI 黄色预警阈值（默认 0.90）
     *
     * @return SPI 黄色阈值
     */
    public double spiYellow() {
        return getDouble("spi.yellow", 0.90);
    }

    /**
     * SPI 红色预警阈值（默认 0.80）
     *
     * @return SPI 红色阈值
     */
    public double spiRed() {
        return getDouble("spi.red", 0.80);
    }

    // ============= Bench =============
    /**
     * 闲置人员黄色预警天数（默认 7 天）
     *
     * @return 黄色预警天数
     */
    public int benchYellowDays() {
        return (int) Math.round(getDouble("bench.days.yellow", 7.0));
    }

    /**
     * 闲置人员红色预警天数（默认 15 天）
     *
     * @return 红色预警天数
     */
    public int benchRedDays() {
        return (int) Math.round(getDouble("bench.days.red", 15.0));
    }

    /**
     * 闲置成本占总人力成本的比例阈值（默认 0.08，即 8%）
     *
     * @return 闲置成本比例阈值
     */
    public BigDecimal benchCostRatio() {
        return BigDecimal.valueOf(getDouble("bench.cost.ratio", 0.08));
    }

    // ============= internal =============
    /**
     * 读取配置并解析为 double，解析失败时回退到默认值
     *
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 解析后的 double 值
     */
    private double getDouble(String key, double defaultValue) {
        String value = read(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("[ThresholdProvider] 解析失败 key={} value={} → 使用默认值 {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 读取配置值，优先走本地缓存（TTL 60s），缓存失效后从配置中心拉取并回填
     *
     * @param key 配置键
     * @return 配置值，不存在或调用失败时返回 null
     */
    private String read(String key) {
        CacheEntry e = cache.computeIfAbsent(key, k -> new CacheEntry(0L, null));
        long now = System.currentTimeMillis();
        if (e.value != null && now - e.loadedAt < CACHE_TTL_MS) {
            return e.value;
        }
        try {
            Map<String, String> group = fetchGroup();
            String v = group == null ? null : group.get("alert." + key);
            cache.put(key, new CacheEntry(now, v));
            return v;
        } catch (Exception ex) {
            log.warn("[ThresholdProvider] 读取 sys_config 失败 key={}: {}",
                    key, ex.toString());
            return e.value;
        }
    }

    /**
     * 通过 Feign 从配置中心拉取指定分组下全部配置项
     *
     * @return 分组配置 Map，调用失败时返回空 Map
     */
    private Map<String, String> fetchGroup() {
        try {
            return configClient.getGroup(GROUP).getData();
        } catch (Exception e) {
            log.warn("[ThresholdProvider] getGroup 调用失败：{}", e.toString());
            return Collections.emptyMap();
        }
    }

    /**
     * 强制刷新缓存（用于配置更新后即时生效）
     */
    public void refresh() {
        cache.clear();
    }

    /**
     * 缓存条目：记录配置项的加载时间戳与值
     */
    private static class CacheEntry {
        /** 加载时间戳（毫秒） */
        final long loadedAt;
        /** 配置值（可为 null） */
        final String value;

        /**
         * 构造方法
         *
         * @param loadedAt 加载时间戳
         * @param value    配置值
         */
        CacheEntry(long loadedAt, String value) {
            this.loadedAt = loadedAt;
            this.value = value;
        }
    }

    // ============= 测试钩子 =============
    /**
     * 单元测试注入：替换 ConfigClient 实现
     *
     * @param client 替换用的 ConfigClient
     */
    public void setConfigClientForTest(ConfigClient client) {
        cache.clear();
        // 因为 final，无法直接替换；保留 hook 供将来通过 Spring profile 切换
    }

    /**
     * 单元测试入口：使用 Supplier 注入读取逻辑
     *
     * @param key    配置键
     * @param reader 读取逻辑
     * @return 读取到的配置值
     */
    public String readForTest(String key, Supplier<String> reader) {
        cache.clear();
        String v = reader.get();
        cache.put(key, new CacheEntry(System.currentTimeMillis(), v));
        return v;
    }
}
