package com.njydsz.pmis.common.redis.service.ops;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.redis.config.RedisProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * Redis 高级操作组件
 *
 * <p>提供 SCAN、Lua 脚本执行、Pipeline 高级用法等操作。
 * SCAN 是替代 KEYS 命令的安全遍历方式，不会阻塞 Redis 服务器。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
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
}
