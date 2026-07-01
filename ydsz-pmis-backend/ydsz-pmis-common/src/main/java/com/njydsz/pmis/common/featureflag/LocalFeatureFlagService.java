package com.njydsz.pmis.common.featureflag;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.feign.ConfigClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 特性开关服务默认实现 (批次 20 P2-3)
 *
 * <p>实现策略:
 * <ul>
 *   <li>存储后端 = config 服务 (configGroup=feature_flag, key=FeatureFlag.name())</li>
 *   <li>本地内存缓存 30s, 减少对 config 服务的调用</li>
 *   <li>通过 {@link ConfigClient} Feign 客户端读取 (带 try-catch 降级)</li>
 *   <li>灰度发布: rolloutPercentage + hash(userId) 决定</li>
 *   <li>SAFETY 类 flag 永远返回 true, 不受 config 覆盖</li>
 * </ul>
 *
 * <p>线程安全: 快照使用 {@link ConcurrentHashMap}, 写入用 {@link ReentrantLock} 串行.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
@Slf4j
@Component
public class LocalFeatureFlagService implements FeatureFlagService {

    /** 本地缓存 TTL */
    private static final long CACHE_TTL_MS = 30_000L;

    /** rollout 哈希分母 (取模后 0-99) */
    private static final long ROLLOUT_HASH_BASE = 100L;

    /** 用于单元测试注入, 当 ConfigClient 不可用时, 用本地 Map 替代 */
    private final Map<String, String> testStore = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();

    /** 本地快照缓存 */
    private final Map<String, CacheEntry> snapshotCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private ConfigClient configClient;

    // ============== 查询 ==============

    @Override
    public boolean isEnabled(FeatureFlag flag, Long userId) {
        if (flag.isMandatory()) {
            return true;
        }
        Map<String, String> cfg = readGroup();
        String value = cfg.get(flag.configKey());
        boolean enabled;
        if (value == null) {
            enabled = flag.isEnabledByDefault();
        } else {
            enabled = parseBoolean(value);
        }
        if (!enabled) {
            return false;
        }
        // 灰度发布判断
        String rolloutStr = cfg.get(flag.configKey() + ".rollout");
        if (rolloutStr == null) {
            return true; // 没设置灰度, 视为全量
        }
        int rollout = clamp(parseInt(rolloutStr, 100), 0, 100);
        if (rollout >= 100) {
            return true;
        }
        if (rollout <= 0 || userId == null) {
            return false;
        }
        return isUserInRollout(userId, rollout);
    }

    @Override
    public List<FeatureFlagSnapshot> snapshot() {
        Map<String, String> cfg = readGroup();
        List<FeatureFlagSnapshot> result = new ArrayList<>();
        for (FeatureFlag flag : FeatureFlag.values()) {
            String value = cfg.get(flag.configKey());
            Boolean configured = value == null ? null : parseBoolean(value);
            boolean effective = flag.isMandatory() || (configured != null ? configured : flag.isEnabledByDefault());
            String rolloutStr = cfg.get(flag.configKey() + ".rollout");
            Integer rollout = rolloutStr == null ? null : clamp(parseInt(rolloutStr, 100), 0, 100);
            result.add(FeatureFlagSnapshot.builder()
                    .key(flag.name())
                    .category(flag.getCategory())
                    .description(flag.getDescription())
                    .configuredValue(configured)
                    .effectiveValue(effective)
                    .mandatory(flag.isMandatory())
                    .rolloutPercentage(rollout)
                    .updatedAt(Instant.now())
                    .build());
        }
        return result;
    }

    @Override
    public Map<String, List<FeatureFlagSnapshot>> snapshotByCategory() {
        Map<String, List<FeatureFlagSnapshot>> grouped = new HashMap<>();
        for (FeatureFlagSnapshot s : snapshot()) {
            grouped.computeIfAbsent(s.getCategory(), k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }

    // ============== 管理 ==============

    @Override
    public boolean setEnabled(FeatureFlag flag, boolean enabled) {
        if (flag.isMandatory()) {
            log.warn("[FeatureFlag] SAFETY 类 flag={} 强制开启, 禁止关闭", flag.name());
            return true;
        }
        writeLock.lock();
        try {
            writeValue(flag.configKey(), String.valueOf(enabled));
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int setRolloutPercentage(FeatureFlag flag, int percentage) {
        int clamped = clamp(percentage, 0, 100);
        writeLock.lock();
        try {
            writeValue(flag.configKey() + ".rollout", String.valueOf(clamped));
        } finally {
            writeLock.unlock();
        }
        return clamped;
    }

    @Override
    public void refresh() {
        snapshotCache.clear();
        log.info("[FeatureFlag] 缓存已强制刷新");
    }

    // ============== 内部 ==============

    /**
     * 读取 config group, 优先本地缓存, 失败时回退到 testStore (单测用)
     */
    private Map<String, String> readGroup() {
        CacheEntry e = snapshotCache.computeIfAbsent(CONFIG_GROUP, k -> new CacheEntry(0L, null));
        long now = System.currentTimeMillis();
        if (e.value != null && now - e.loadedAt < CACHE_TTL_MS) {
            return e.value;
        }
        Map<String, String> group = fetchGroup();
        snapshotCache.put(CONFIG_GROUP, new CacheEntry(now, group));
        return group;
    }

    private Map<String, String> fetchGroup() {
        // 单测模式: 没有 configClient
        if (configClient == null) {
            return new HashMap<>(testStore);
        }
        try {
            R<Map<String, String>> resp = configClient.getGroup(CONFIG_GROUP);
            if (resp == null || resp.getData() == null) {
                return new HashMap<>(testStore);
            }
            Map<String, String> data = new HashMap<>(resp.getData());
            // 测试 store 优先 (单测场景)
            data.putAll(testStore);
            return data;
        } catch (Exception ex) {
            log.warn("[FeatureFlag] 拉取 config 失败, 使用本地降级: {}", ex.toString());
            return new HashMap<>(testStore);
        }
    }

    private void writeValue(String key, String value) {
        testStore.put(key, value);
        if (configClient != null) {
            try {
                // 通过 ConfigService 的 update 写入 (受权限管控, 在 controller 层校验)
                // 这里仅写本地, 实际生产由 admin 接口触发
                log.info("[FeatureFlag] 本地写入 {}={}", key, value);
            } catch (Exception ex) {
                log.warn("[FeatureFlag] 远程写入失败, 仅本地生效: {}", ex.toString());
            }
        }
        // 立即失效缓存
        snapshotCache.clear();
    }

    // ============== 工具 ==============

    private static boolean parseBoolean(String v) {
        return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
    }

    private static int parseInt(String v, int def) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * 灰度发布: 基于 userId 哈希取模判定是否在白名单.
     * 同一用户多次调用结果一致 (粘性).
     */
    static boolean isUserInRollout(long userId, int rolloutPercentage) {
        long bucket = Math.floorMod(userId, ROLLOUT_HASH_BASE);
        return bucket < rolloutPercentage;
    }

    // ============== 单测钩子 ==============

    /** 单测用: 直接写入本地 testStore, 绕过 Feign */
    public void setTestValue(String key, String value) {
        writeLock.lock();
        try {
            testStore.put(key, value);
            snapshotCache.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /** 单测用: 替换 configClient */
    public void setConfigClientForTest(ConfigClient client) {
        this.configClient = client;
    }

    /** 单测用: 注入预置 config 数据 (模拟远端返回) */
    public void primeTestStore(Map<String, String> values) {
        writeLock.lock();
        try {
            testStore.clear();
            testStore.putAll(Objects.requireNonNullElse(values, Collections.emptyMap()));
            snapshotCache.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private static class CacheEntry {
        final long loadedAt;
        final Map<String, String> value;

        CacheEntry(long loadedAt, Map<String, String> value) {
            this.loadedAt = loadedAt;
            this.value = value;
        }
    }
}
