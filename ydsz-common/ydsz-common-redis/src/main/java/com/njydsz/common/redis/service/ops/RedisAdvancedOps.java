package com.njydsz.common.redis.service.ops;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import com.njydsz.common.redis.cluster.ClusterSlotUtil;
import com.njydsz.common.redis.config.RedisProperties;

/**
 * Redis 高级操作组件
 *
 * <p>提供 SCAN、Lua 脚本执行、Pipeline 高级用法等操作。
 * SCAN 是替代 KEYS 命令的安全遍历方式，不会阻塞 Redis 服务器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class RedisAdvancedOps {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;

    public RedisAdvancedOps(RedisTemplate<String, Object> redisTemplate, RedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = redisProperties != null ? (redisProperties.getKeyPrefix() != null ? redisProperties.getKeyPrefix() : "") : "";
    }

    /**
     * 格式化 Key，添加统一前缀
     */
    private String formatKey(String key) {
        if (key == null || keyPrefix.isEmpty()) {
            return key;
        }
        return keyPrefix + ":" + key;
    }

    /**
     * 批量格式化 Keys
     */
    private List<String> formatKeys(List<String> keys) {
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream().map(this::formatKey).collect(Collectors.toList());
    }

    // ============================ SCAN 操作 =============================

    /**
     * 使用 SCAN 分批删除匹配的键
     *
     * <p>基于 SCAN 命令增量迭代匹配的键，避免 KEYS 命令的阻塞问题。
     * SCAN 的遍历特性：在迭代期间新增的键可能被返回，删除的键可能不会被返回。
     *
     * @param pattern 匹配模式
     * @return 删除的键数量
     */
    public long deleteByPattern(String pattern) {
        return deleteByPattern(pattern, 100);
    }

    /**
     * 使用 SCAN 分批删除匹配的键
     *
     * <p>基于 SCAN 命令增量迭代匹配的键，避免 KEYS 命令的阻塞问题。
     * SCAN 的遍历特性：在迭代期间新增的键可能被返回，删除的键可能不会被返回。
     * 收集的键按批次调用 delete(Collection) 批量删除，减少网络往返。
     *
     * @param pattern 匹配模式
     * @param count   每次迭代返回的元素数量提示值
     * @return 删除的键数量
     */
    public long deleteByPattern(String pattern, int count) {
        if (pattern == null || pattern.isEmpty()) {
            return 0;
        }
        try {
            long deleted = 0;
            String scanPattern = keyPrefix.isEmpty() ? pattern : keyPrefix + ":" + pattern;
            ScanOptions options = ScanOptions.scanOptions()
                    .match(scanPattern)
                    .count(count)
                    .build();
            try (Cursor<byte[]> cursor = redisTemplate.execute((RedisCallback<Cursor<byte[]>>) connection ->
                    connection.keyCommands().scan(options))) {
                if (cursor != null) {
                    List<String> batch = new ArrayList<>(200);
                    int prefixLen = keyPrefix.isEmpty() ? 0 : keyPrefix.length() + 1;
                    while (cursor.hasNext()) {
                        String fullKey = new String(cursor.next(), StandardCharsets.UTF_8);
                        batch.add(keyPrefix.isEmpty() ? fullKey : fullKey.substring(prefixLen));
                        if (batch.size() >= 200) {
                            Long batchDeleted = redisTemplate.delete(formatKeys(batch));
                            deleted += (batchDeleted != null ? batchDeleted : 0);
                            batch.clear();
                        }
                    }
                    if (!batch.isEmpty()) {
                        Long batchDeleted = redisTemplate.delete(formatKeys(batch));
                        deleted += (batchDeleted != null ? batchDeleted : 0);
                    }
                }
            }
            return deleted;
        } catch (Exception e) {
            log.error("【Redis】SCAN 删除键失败 | pattern={} | error={}", pattern, e);
            return 0;
        }
    }

    // ============================ Pipeline 批量操作 =============================

    /**
     * 执行 Pipeline 批量操作
     *
     * @param action 批量操作函数
     * @return 操作结果列表
     */
    public List<Object> executePipelined(RedisCallback<?> action) {
        if (action == null) {
            return Collections.emptyList();
        }
        try {
            List<Object> results = redisTemplate.executePipelined(action);
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.error("【Redis】Pipeline 执行失败 | error={}", e);
            return Collections.emptyList();
        }
    }

    /**
     * 执行 Pipeline 批量操作（带序列化）
     *
     * @param action 批量操作函数
     * @param clazz  结果元素类型
     * @param <T>    结果类型
     * @return 操作结果列表
     */
    public <T> List<T> executePipelined(SessionCallback<T> action, Class<T> clazz) {
        if (action == null) {
            return Collections.emptyList();
        }
        try {
            List<Object> results = redisTemplate.executePipelined(action);
            return results.stream().map(clazz::cast).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("【Redis】Pipeline 执行失败 | error={}", e);
            return Collections.emptyList();
        }
    }

    /**
     * 以 Consumer 形式执行 Pipeline 批量操作（可读性优先的便捷封装）。
     *
     * <p>与 {@link #executePipelined(RedisCallback)} 不同，此处不要求实现 {@code RedisCallback}，
     * 调用方只需在 {@code operations} 中向传入的 {@code RedisTemplate} 发起命令，框架自动将其收集进 Pipeline。
     * {@code operations} 为 null 时直接返回空列表，不抛异常；执行失败降级返回空列表并记录错误日志。
     *
     * @param operations 累积到同一 Pipeline 的命令集合，可能为 null
     * @param <T> 结果元素类型（实际由 Pipeline 返回决定）
     * @return Pipeline 执行结果列表，可能为 null 元素（命令未返回时）
     */
    public <T> List<T> executePipelinedWithConsumer(Consumer<RedisTemplate<String, Object>> operations) {
        if (operations == null) {
            return Collections.emptyList();
        }
        try {
            return (List<T>) redisTemplate.executePipelined((RedisCallback<T>) connection -> {
                operations.accept(redisTemplate);
                return null;
            });
        } catch (Exception e) {
            log.error("【Redis】Pipeline 执行失败 | error={}", e);
            return Collections.emptyList();
        }
    }

    // ============================ Lua 脚本操作 =============================

    /**
     * 执行 Lua 脚本
     *
     * @param script     脚本内容
     * @param keys       键列表
     * @param returnType 返回值类型
     * @param args       参数列表
     * @param <T>        返回值类型
     * @return 脚本执行结果
     */
    public <T> T executeScript(String script, List<String> keys, Class<T> returnType, Object... args) {
        if (script == null) {
            return null;
        }
        try {
            List<String> formattedKeys = formatKeys(keys);
            DefaultRedisScript<T> redisScript = new DefaultRedisScript<>(script, returnType);
            redisScript.setScriptText(script);
            return redisTemplate.execute(redisScript, formattedKeys, args);
        } catch (Exception e) {
            log.error("【Redis】Lua 脚本执行失败 | script={} | error={}", script, e);
            return null;
        }
    }

    // ============================ Cluster 模式 Pipeline =============================

    /**
     * Cluster 模式下批量 GET（按槽位分批）
     *
     * <p>自动将 Key 按 Redis Cluster 槽位分组，对每组发起 multiGet 请求，
     * 避免 Cluster 模式下跨槽 MOVED/ASK 异常。
     *
     * @param keys Key 列表
     * @return Key-Value 映射（Key 为原始 Key，不含前缀）
     */
    public Map<String, Object> multiGetClusterAware(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<Integer, List<String>> grouped = ClusterSlotUtil.groupBySlot(keys, k -> k);
            Map<String, Object> result = new LinkedHashMap<>(keys.size());
            for (List<String> slotKeys : grouped.values()) {
                List<String> formattedKeys = formatKeys(slotKeys);
                List<Object> values = redisTemplate.opsForValue().multiGet(formattedKeys);
                if (values != null) {
                    for (int i = 0; i < slotKeys.size(); i++) {
                        result.put(slotKeys.get(i), values.get(i));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.error("【Redis】Cluster multiGet 失败 | keys={} | error={}", keys, e);
            return Collections.emptyMap();
        }
    }

    /**
     * Cluster 模式下批量 SET（按槽位分批 Pipeline）
     *
     * @param keyValueMap Key-Value 映射
     */
    public void multiSetClusterAware(Map<String, Object> keyValueMap) {
        if (keyValueMap == null || keyValueMap.isEmpty()) {
            return;
        }
        try {
            List<String> keys = new ArrayList<>(keyValueMap.keySet());
            Map<Integer, List<String>> grouped = ClusterSlotUtil.groupBySlot(keys, k -> k);
            for (List<String> slotKeys : grouped.values()) {
                redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (String key : slotKeys) {
                        String formattedKey = formatKey(key);
                        Object value = keyValueMap.get(key);
                        byte[] rawKey = redisTemplate.getStringSerializer().serialize(formattedKey);
                        byte[] rawValue = ((RedisSerializer<Object>) redisTemplate.getValueSerializer()).serialize(value);
                        if (rawKey != null && rawValue != null) {
                            connection.stringCommands().set(rawKey, rawValue);
                        }
                    }
                    return null;
                });
            }
        } catch (Exception e) {
            log.error("【Redis】Cluster multiSet 失败 | error={}", e);
        }
    }

    /**
     * Cluster 模式下批量 DELETE（按槽位分批）
     *
     * @param keys Key 列表
     * @return 删除的 Key 数量
     */
    public long deleteClusterAware(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        try {
            Map<Integer, List<String>> grouped = ClusterSlotUtil.groupBySlot(keys, k -> k);
            long totalDeleted = 0;
            for (List<String> slotKeys : grouped.values()) {
                List<String> formattedKeys = formatKeys(slotKeys);
                Long deleted = redisTemplate.delete(formattedKeys);
                totalDeleted += deleted != null ? deleted : 0;
            }
            return totalDeleted;
        } catch (Exception e) {
            log.error("【Redis】Cluster delete 失败 | keys={} | error={}", keys, e);
            return 0;
        }
    }
}
