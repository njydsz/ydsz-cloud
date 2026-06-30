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
 * 优先级：sys_config (pmis_cfg.pmis_config) > 静态默认值。
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdProvider {

    public static final String GROUP = "alert";

    private static final long CACHE_TTL_MS = 60_000L;

    private final ConfigClient configClient;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // ============= CPI / SPI =============
    public double cpiYellow() {
        return getDouble("cpi.yellow", 0.95);
    }

    public double cpiRed() {
        return getDouble("cpi.red", 0.85);
    }

    public double spiYellow() {
        return getDouble("spi.yellow", 0.90);
    }

    public double spiRed() {
        return getDouble("spi.red", 0.80);
    }

    // ============= Bench =============
    public int benchYellowDays() {
        return (int) Math.round(getDouble("bench.days.yellow", 7.0));
    }

    public int benchRedDays() {
        return (int) Math.round(getDouble("bench.days.red", 15.0));
    }

    public BigDecimal benchCostRatio() {
        return BigDecimal.valueOf(getDouble("bench.cost.ratio", 0.08));
    }

    // ============= internal =============
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

    private Map<String, String> fetchGroup() {
        try {
            return configClient.getGroup(GROUP).getData();
        } catch (Exception e) {
            log.warn("[ThresholdProvider] getGroup 调用失败：{}", e.toString());
            return Collections.emptyMap();
        }
    }

    /** 强制刷新缓存（用于配置更新后即时生效） */
    public void refresh() {
        cache.clear();
    }

    private static class CacheEntry {
        final long loadedAt;
        final String value;

        CacheEntry(long loadedAt, String value) {
            this.loadedAt = loadedAt;
            this.value = value;
        }
    }

    // ============= 测试钩子 =============
    /** 单元测试注入：替换 ConfigClient 实现 */
    public void setConfigClientForTest(ConfigClient client) {
        cache.clear();
        // 因为 final，无法直接替换；保留 hook 供将来通过 Spring profile 切换
    }

    /** 单元测试入口：使用 Supplier 注入读取逻辑 */
    public String readForTest(String key, Supplier<String> reader) {
        cache.clear();
        String v = reader.get();
        cache.put(key, new CacheEntry(System.currentTimeMillis(), v));
        return v;
    }
}
