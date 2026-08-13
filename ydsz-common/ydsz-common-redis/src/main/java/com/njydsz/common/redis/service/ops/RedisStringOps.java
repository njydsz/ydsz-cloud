package com.njydsz.common.redis.service.ops;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.njydsz.common.redis.config.RedisProperties;
import com.njydsz.common.redis.enums.RedisKeysEnum;
import com.njydsz.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.common.util.collection.CollectionUtils;
import com.njydsz.common.json.YdszJson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis String / Bitmap 操作组件
 *
 * <p>按数据类型拆分而来的细粒度操作组件，职责单一，便于维护。
 * 包含：通用操作、String 操作、Bitmap 操作。
 *
 * <p><b>增强特性：</b>
 * <ul>
 *   <li>Lua 脚本保证 getOrCompute 锁释放的原子性</li>
 *   <li>Micrometer 指标采集（可选）</li>
 *   <li>过期时间随机偏移防止缓存雪崩</li>
 *   <li>统一 Key 前缀，支持多应用共享 Redis</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Slf4j
@RequiredArgsConstructor
public class RedisStringOps {

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private static final long CACHE_EXPIRE_JITTER_RATIO = 10;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisProperties redisProperties;
    private final RedisMetricsCollector metricsCollector;

    // ============================ 通用操作 =============================

    /**
     * 格式化 Key，添加统一前缀
     */
    private String formatKey(String key) {
        if (key == null) {
            return null;
        }
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        if (prefix == null || prefix.isEmpty()) {
            return key;
        }
        return prefix + ":" + key;
    }

    /**
     * Bean 初始化后的启动校验钩子（fail-fast）。
     *
     * <p>由 {@code @PostConstruct} 在依赖注入完成后触发，强校验 {@code RedisTemplate} 与其
     * {@code ConnectionFactory} 已就绪，缺失则抛出 {@link NullPointerException} 阻止应用启动，
     * 避免运行期才暴露配置问题。同时打印当前 key 前缀，便于确认多应用共享 Redis 时的命名空间隔离是否生效。
     */
    @PostConstruct
    public void init() {
        Objects.requireNonNull(redisTemplate, "RedisTemplate 未正确初始化");
        Objects.requireNonNull(redisTemplate.getConnectionFactory(), "RedisConnectionFactory 未配置");
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        log.info("【Redis】RedisStringOps 初始化完成 | keyPrefix={}", prefix == null || prefix.isEmpty() ? "无" : prefix);
    }

    /**
     * 批量格式化 Keys
     */
    private List<String> formatKeys(Collection<String> keys) {
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream().map(this::formatKey).collect(Collectors.toList());
    }

    /**
     * 设置键的过期时间
     *
     * @param key  键
     * @param time 过期时间（秒）
     * @return true-设置成功，false-设置失败或键不存在
     */
    public boolean expire(String key, long time) {
        if (key == null || time <= 0) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("expire", () -> Boolean.TRUE.equals(redisTemplate.expire(formattedKey, Duration.ofSeconds(time))))
                    : Boolean.TRUE.equals(redisTemplate.expire(formattedKey, Duration.ofSeconds(time)));
        } catch (Exception e) {
            recordError("expire", e);
            log.error("【Redis】设置过期时间失败 | key={} | time={} | error={}", key, time, e);
            return false;
        }
    }

    /**
     * 设置键的过期时间（使用 Duration）
     *
     * @param key     键
     * @param duration 过期时间
     * @return true-设置成功，false-设置失败
     */
    public boolean expire(String key, Duration duration) {
        if (key == null || duration == null || duration.isNegative() || duration.isZero()) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("expire", () -> Boolean.TRUE.equals(redisTemplate.expire(formattedKey, duration)))
                    : Boolean.TRUE.equals(redisTemplate.expire(formattedKey, duration));
        } catch (Exception e) {
            recordError("expire", e);
            log.error("【Redis】设置过期时间失败 | key={} | duration={} | error={}", key, duration, e);
            return false;
        }
    }

    /**
     * 获取键的过期时间
     *
     * @param key 键
     * @return 过期时间（秒），-1-永久有效，-2-键不存在
     */
    public long getExpire(String key) {
        if (key == null) {
            return -2;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("getExpire", () -> {
                        Long expire = redisTemplate.getExpire(formattedKey, TimeUnit.SECONDS);
                        return expire != null ? expire : -2L;
                    })
                    : Optional.ofNullable(redisTemplate.getExpire(formattedKey, TimeUnit.SECONDS)).orElse(-2L);
        } catch (Exception e) {
            recordError("getExpire", e);
            log.error("【Redis】获取过期时间失败 | key={} | error={}", key, e);
            return -2;
        }
    }

    /**
     * 检查键是否存在
     *
     * @param key 键
     * @return true-存在，false-不存在
     */
    public boolean hasKey(String key) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("hasKey", () -> Boolean.TRUE.equals(redisTemplate.hasKey(formattedKey)))
                    : Boolean.TRUE.equals(redisTemplate.hasKey(formattedKey));
        } catch (Exception e) {
            recordError("hasKey", e);
            log.error("【Redis】检查键是否存在失败 | key={} | error={}", key, e);
            return false;
        }
    }

    /**
     * 删除键
     *
     * @param keys 键数组
     */
    public void del(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            List<String> formattedKeys = formatKeys(Arrays.asList(keys));
            Runnable action = () -> {
                if (formattedKeys.size() == 1) {
                    redisTemplate.delete(formattedKeys.get(0));
                } else {
                    redisTemplate.delete(formattedKeys);
                }
            };
            if (metricsCollector != null) {
                metricsCollector.recordOperation("del", action);
            } else {
                action.run();
            }
        } catch (Exception e) {
            recordError("del", e);
            log.error("【Redis】删除键失败 | keys={} | error={}", Arrays.toString(keys), e);
        }
    }

    /**
     * 删除键（集合形式）
     *
     * @param keys 键集合
     */
    public void del(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return;
        }
        try {
            List<String> formattedKeys = formatKeys(keys);
            if (metricsCollector != null) {
                metricsCollector.recordOperation("del", () -> redisTemplate.delete(formattedKeys));
            } else {
                redisTemplate.delete(formattedKeys);
            }
        } catch (Exception e) {
            recordError("del", e);
            log.error("【Redis】删除键失败 | keys={} | error={}", keys, e);
        }
    }

    /**
     * 批量删除匹配模式的键（使用 SCAN，安全）
     *
     * @param pattern 匹配模式，如 "user:*"
     * @return 删除的键数量
     */
    public long delByPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return 0;
        }
        try {
            Set<String> keys = scan(pattern);
            if (CollectionUtils.isNotEmpty(keys)) {
                Long deleted = redisTemplate.delete(keys);
                return deleted != null ? deleted : 0;
            }
            return 0;
        } catch (Exception e) {
            recordError("delByPattern", e);
            log.error("【Redis】批量删除键失败 | pattern={} | error={}", pattern, e);
            return 0;
        }
    }

    /**
     * 使用 SCAN 命令搜索键（避免 KEYS 命令阻塞）
     *
     * <p>默认限制最大返回数量为 10000，防止大数据量场景 OOM。
     *
     * @param pattern 匹配模式
     * @return 匹配的键集合
     */
    public Set<String> scan(String pattern) {
        return scan(pattern, 10000);
    }

    /**
     * 使用 SCAN 命令搜索键（避免 KEYS 命令阻塞）
     *
     * @param pattern 匹配模式
     * @param maxKeys 最大返回键数量（防止 OOM）
     * @return 匹配的键集合
     */
    public Set<String> scan(String pattern, int maxKeys) {
        if (pattern == null || pattern.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            Set<String> keys = new HashSet<>(Math.min(maxKeys, 1024));
            String keyPrefixStr = redisProperties != null ? redisProperties.getKeyPrefix() : null;
            String scanPattern = keyPrefixStr == null || keyPrefixStr.isEmpty() ? pattern : keyPrefixStr + ":" + pattern;
            ScanOptions options = ScanOptions.scanOptions().match(scanPattern).count(1000).build();
            try (Cursor<byte[]> cursor = redisTemplate.execute((RedisCallback<Cursor<byte[]>>) connection ->
                    connection.keyCommands().scan(options))) {
                if (cursor != null) {
                    int prefixLen = keyPrefixStr == null || keyPrefixStr.isEmpty() ? 0 : keyPrefixStr.length() + 1;
                    while (cursor.hasNext() && keys.size() < maxKeys) {
                        String fullKey = new String(cursor.next(), StandardCharsets.UTF_8);
                        // Strip prefix from returned keys
                        keys.add(keyPrefixStr == null || keyPrefixStr.isEmpty() ? fullKey : fullKey.substring(prefixLen));
                    }
                }
            }
            return keys;
        } catch (Exception e) {
            recordError("scan", e);
            log.error("【Redis】SCAN 操作失败 | pattern={} | error={}", pattern, e);
            return Collections.emptySet();
        }
    }

    /**
     * 重命名键
     *
     * @param oldKey 旧键名
     * @param newKey 新键名
     * @return true-重命名成功
     */
    public boolean rename(String oldKey, String newKey) {
        if (oldKey == null || newKey == null) {
            return false;
        }
        try {
            String formattedOldKey = formatKey(oldKey);
            String formattedNewKey = formatKey(newKey);
            if (metricsCollector != null) {
                return metricsCollector.recordOperation("rename", () -> {
                    redisTemplate.rename(formattedOldKey, formattedNewKey);
                    return true;
                });
            }
            redisTemplate.rename(formattedOldKey, formattedNewKey);
            return true;
        } catch (Exception e) {
            recordError("rename", e);
            log.error("【Redis】重命名键失败 | oldKey={} | newKey={} | error={}", oldKey, newKey, e);
            return false;
        }
    }

    /**
     * 当新键不存在时重命名
     *
     * @param oldKey 旧键名
     * @param newKey 新键名
     * @return true-重命名成功
     */
    public boolean renameIfAbsent(String oldKey, String newKey) {
        if (oldKey == null || newKey == null) {
            return false;
        }
        String formattedOldKey = formatKey(oldKey);
        String formattedNewKey = formatKey(newKey);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("renameIfAbsent", () -> Boolean.TRUE.equals(redisTemplate.renameIfAbsent(formattedOldKey, formattedNewKey)))
                    : Boolean.TRUE.equals(redisTemplate.renameIfAbsent(formattedOldKey, formattedNewKey));
        } catch (Exception e) {
            recordError("renameIfAbsent", e);
            log.error("【Redis】条件重命名失败 | oldKey={} | newKey={} | error={}", oldKey, newKey, e);
            return false;
        }
    }

    // ============================ String 操作 =============================

    /**
     * 获取值
     *
     * @param key 键
     * @return 值，不存在时返回 null。如需类型安全转换，请使用 {@link #get(String, Class)}
     */
    public Object get(String key) {
        if (key == null) {
            return null;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("get", () -> redisTemplate.opsForValue().get(formattedKey))
                    : redisTemplate.opsForValue().get(formattedKey);
        } catch (RedisConnectionFailureException e) {
            recordError("get", e);
            log.error("【Redis】连接失败，GET 操作降级返回 null | key={} | error={}", key, e);
            return null;
        } catch (Exception e) {
            recordError("get", e);
            log.error("【Redis】GET 操作失败 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 获取值（带类型转换）
     *
     * @param key   键
     * @param clazz 目标类型
     * @param <T>   值类型
     * @return 值
     */
    public <T> T get(String key, Class<T> clazz) {
        Objects.requireNonNull(clazz, "目标类型不能为 null");
        if (key == null) {
            return null;
        }
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        try {
            String json = YdszJson.toJson(value);
            return YdszJson.fromJson(json, clazz);
        } catch (Exception e) {
            log.error("【Redis】类型转换失败 | key={} | targetClass={} | error={}", key, clazz.getName(), e);
            return null;
        }
    }

    /**
     * 设置值
     *
     * @param key   键
     * @param value 值
     * @return true-设置成功
     */
    public boolean set(String key, Object value) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            if (metricsCollector != null) {
                metricsCollector.recordOperation("set", () -> redisTemplate.opsForValue().set(formattedKey, value));
            } else {
                redisTemplate.opsForValue().set(formattedKey, value);
            }
            return true;
        } catch (RedisConnectionFailureException e) {
            recordError("set", e);
            log.error("【Redis】连接失败，SET 操作降级返回 false | key={} | error={}", key, e);
            return false;
        } catch (Exception e) {
            recordError("set", e);
            log.error("【Redis】SET 操作失败 | key={} | error={}", key, e);
            return false;
        }
    }

    /**
     * 设置值（带过期时间，自动添加随机偏移防止雪崩）
     *
     * @param key   键
     * @param value 值
     * @param time  过期时间（秒）
     * @return true-设置成功
     */
    public boolean set(String key, Object value, long time) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            long expireWithJitter = addJitter(time);
            Runnable action = () -> redisTemplate.opsForValue().set(formattedKey, value, Duration.ofSeconds(expireWithJitter));
            if (metricsCollector != null) {
                metricsCollector.recordOperation("set", action);
            } else {
                action.run();
            }
            return true;
        } catch (RedisConnectionFailureException e) {
            recordError("set", e);
            log.error("【Redis】连接失败，SET 操作降级返回 false | key={} | time={} | error={}", key, time, e);
            return false;
        } catch (Exception e) {
            recordError("set", e);
            log.error("【Redis】SET 操作失败 | key={} | time={} | error={}", key, time, e);
            return false;
        }
    }

    /**
     * 设置值（带过期时间 Duration）
     *
     * @param key      键
     * @param value    值
     * @param duration 过期时间
     * @return true-设置成功
     */
    public boolean set(String key, Object value, Duration duration) {
        if (key == null || duration == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            Duration jittered = addJitter(duration);
            Runnable action = () -> redisTemplate.opsForValue().set(formattedKey, value, jittered);
            if (metricsCollector != null) {
                metricsCollector.recordOperation("set", action);
            } else {
                action.run();
            }
            return true;
        } catch (RedisConnectionFailureException e) {
            recordError("set", e);
            log.error("【Redis】连接失败，SET 操作降级返回 false | key={} | duration={} | error={}", key, duration, e);
            return false;
        } catch (Exception e) {
            recordError("set", e);
            log.error("【Redis】SET 操作失败 | key={} | duration={} | error={}", key, duration, e);
            return false;
        }
    }

    /**
     * 只有在键不存在时设置
     *
     * @param key    键
     * @param value  值
     * @param expire 过期时间（秒）
     * @return true-设置成功（键原本不存在）
     */
    public boolean setIfAbsent(String key, Object value, long expire) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            long expireWithJitter = addJitter(expire);
            if (expireWithJitter > 0) {
                return metricsCollector != null
                        ? metricsCollector.recordOperation("setIfAbsent", () -> Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(formattedKey, value, Duration.ofSeconds(expireWithJitter))))
                        : Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(formattedKey, value, Duration.ofSeconds(expireWithJitter)));
            } else {
                return metricsCollector != null
                        ? metricsCollector.recordOperation("setIfAbsent", () -> Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(formattedKey, value)))
                        : Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(formattedKey, value));
            }
        } catch (Exception e) {
            recordError("setIfAbsent", e);
            log.error("【Redis】SETNX 操作失败 | key={} | expire={} | error={}", key, expire, e);
            return false;
        }
    }

    /**
     * 只有在键存在时设置
     *
     * @param key    键
     * @param value  值
     * @param expire 过期时间（秒）
     * @return true-设置成功（键原本存在）
     */
    public boolean setIfPresent(String key, Object value, long expire) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            long expireWithJitter = addJitter(expire);
            if (expireWithJitter > 0) {
                return metricsCollector != null
                        ? metricsCollector.recordOperation("setIfPresent", () -> Boolean.TRUE.equals(redisTemplate.opsForValue().setIfPresent(formattedKey, value, Duration.ofSeconds(expireWithJitter))))
                        : Boolean.TRUE.equals(redisTemplate.opsForValue().setIfPresent(formattedKey, value, Duration.ofSeconds(expireWithJitter)));
            } else {
                return metricsCollector != null
                        ? metricsCollector.recordOperation("setIfPresent", () -> Boolean.TRUE.equals(redisTemplate.opsForValue().setIfPresent(formattedKey, value)))
                        : Boolean.TRUE.equals(redisTemplate.opsForValue().setIfPresent(formattedKey, value));
            }
        } catch (Exception e) {
            recordError("setIfPresent", e);
            log.error("【Redis】SETXX 操作失败 | key={} | expire={} | error={}", key, expire, e);
            return false;
        }
    }

    /**
     * 缓存穿透保护：获取缓存，若不存在则通过 supplier 获取并缓存
     *
     * <p>使用 Lua 脚本保证锁释放的原子性（校验锁持有者），防止误删其他线程的锁。
     *
     * @param key      缓存键
     * @param expire   过期时间（秒）
     * @param supplier 数据提供函数
     * @param clazz    值类型
     * @param <T>      值类型
     * @return 缓存值
     */
    public <T> T getOrCompute(String key, long expire, Supplier<T> supplier, Class<T> clazz) {
        if (key == null) {
            return null;
        }
        T value = get(key, clazz);
        if (value != null) {
            return value;
        }
        String lockKey = "lock:compute:" + key;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(formatKey(lockKey), lockValue, Duration.ofSeconds(30));
        if (Boolean.TRUE.equals(locked)) {
            try {
                value = get(key, clazz);
                if (value != null) {
                    return value;
                }
                if (supplier != null) {
                    value = supplier.get();
                    if (value != null) {
                        set(key, value, expire);
                    } else {
                        set(key, NullPlaceholder.INSTANCE, Math.min(expire, 60));
                    }
                }
            } finally {
                releaseLock(lockKey, lockValue);
            }
        } else {
            long waitNanos = TimeUnit.MILLISECONDS.toNanos(10);
            long maxWaitNanos = TimeUnit.MILLISECONDS.toNanos(3000);
            long totalWaitNanos = 0;
            while (totalWaitNanos < maxWaitNanos) {
                LockSupport.parkNanos(waitNanos);
                totalWaitNanos += waitNanos;
                waitNanos = Math.min(waitNanos * 2, maxWaitNanos - totalWaitNanos);
                value = get(key, clazz);
                if (value != null) {
                    return value;
                }
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (value == null && supplier != null) {
                value = supplier.get();
                if (value != null) {
                    set(key, value, expire);
                }
            }
        }
        return value;
    }

    /**
     * 空值占位符（缓存穿透保护用）。
     *
     * <p>当业务值本身为 null 时，缓存此占位对象以避免缓存穿透
     * （即空值也短暂缓存，防止大量不存在 key 的请求直击数据库）。</p>
     */
    private static class NullPlaceholder {
        /** 单例占位实例 */
        static final Object INSTANCE = new NullPlaceholder();
    }

    /**
     * 缓存穿透保护：获取缓存，若不存在则通过 supplier 获取并缓存（使用枚举 Key）
     *
     * @param keyEnum  键枚举
     * @param arg      键参数
     * @param expire   过期时间（秒）
     * @param supplier 数据提供函数
     * @param clazz    值类型
     * @param <T>      值类型
     * @return 缓存值
     */
    public <T> T getOrCompute(RedisKeysEnum keyEnum, Object arg, long expire, Supplier<T> supplier, Class<T> clazz) {
        return getOrCompute(keyEnum.join(arg), expire, supplier, clazz);
    }

    /**
     * 递增操作
     *
     * @param key   键
     * @param delta 增量（必须大于 0）
     * @return 递增后的值
     */
    public long incr(String key, long delta) {
        if (key == null || delta <= 0) {
            throw new IllegalArgumentException("增量必须大于 0");
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("incr", () -> {
                        Long result = redisTemplate.opsForValue().increment(formattedKey, delta);
                        return result != null ? result : 0L;
                    })
                    : Optional.ofNullable(redisTemplate.opsForValue().increment(formattedKey, delta)).orElse(0L);
        } catch (Exception e) {
            recordError("incr", e);
            log.error("【Redis】INCR 操作失败 | key={} | delta={} | error={}", key, delta, e);
            return 0;
        }
    }

    /**
     * 递减操作
     *
     * @param key   键
     * @param delta 减量（必须大于 0）
     * @return 递减后的值
     */
    public long decr(String key, long delta) {
        if (key == null || delta <= 0) {
            throw new IllegalArgumentException("减量必须大于 0");
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("decr", () -> {
                        Long result = redisTemplate.opsForValue().increment(formattedKey, -delta);
                        return result != null ? result : 0L;
                    })
                    : Optional.ofNullable(redisTemplate.opsForValue().increment(formattedKey, -delta)).orElse(0L);
        } catch (Exception e) {
            recordError("decr", e);
            log.error("【Redis】DECR 操作失败 | key={} | delta={} | error={}", key, delta, e);
            return 0;
        }
    }

    /**
     * 原子递增（浮点数）
     *
     * @param key   键
     * @param delta 增量
     * @return 递增后的值
     */
    public double incrByFloat(String key, double delta) {
        if (key == null) {
            throw new IllegalArgumentException("键不能为空");
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("incrByFloat", () -> {
                        Double result = redisTemplate.opsForValue().increment(formattedKey, delta);
                        return result != null ? result : 0.0;
                    })
                    : Optional.ofNullable(redisTemplate.opsForValue().increment(formattedKey, delta)).orElse(0.0);
        } catch (Exception e) {
            recordError("incrByFloat", e);
            log.error("【Redis】INCRBYFLOAT 操作失败 | key={} | delta={} | error={}", key, delta, e);
            return 0.0;
        }
    }

    /**
     * 批量获取值
     *
     * @param keys 键集合
     * @return 值列表（与 keys 顺序对应）
     */
    public List<String> mget(List<String> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptyList();
        }
        try {
            List<String> formattedKeys = formatKeys(keys);
            List<byte[]> rawKeys = formattedKeys.stream()
                    .map(k -> redisTemplate.getStringSerializer().serialize(k))
                    .collect(Collectors.toList());
            byte[][] rawKeysArray = rawKeys.toArray(new byte[0][]);
            List<byte[]> rawValues = redisTemplate.execute((RedisCallback<List<byte[]>>) connection ->
                    connection.stringCommands().mGet(rawKeysArray));
            if (rawValues == null) {
                return Collections.emptyList();
            }
            return rawValues.stream()
                    .map(b -> b == null ? null : new String(b, StandardCharsets.UTF_8))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            recordError("mget", e);
            log.error("【Redis】MGET 操作失败 | keys={} | error={}", keys, e);
            return Collections.emptyList();
        }
    }

    /**
     * 批量获取值（泛型版本）
     *
     * @param keys  键集合
     * @param clazz 值类型
     * @param <T>   值类型
     * @return 值列表
     */
    public <T> List<T> mgetObjects(List<String> keys, Class<T> clazz) {
        if (CollectionUtils.isEmpty(keys)) {
            return Collections.emptyList();
        }
        try {
            List<String> formattedKeys = formatKeys(keys);
            List<Object> rawResults = redisTemplate.opsForValue().multiGet(formattedKeys);
            if (rawResults == null) {
                return Collections.emptyList();
            }
            return rawResults.stream().map(clazz::cast).collect(Collectors.toList());
        } catch (Exception e) {
            recordError("mgetObjects", e);
            log.error("【Redis】MGET 操作失败 | keys={} | error={}", keys, e);
            return Collections.emptyList();
        }
    }

    // ============================ Bitmap 操作 =============================

    /**
     * 设置位图
     *
     * @param key    键
     * @param offset 偏移量
     * @param value  值（true-1，false-0）
     * @return true-设置成功
     */
    public boolean setBit(String key, long offset, boolean value) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("setBit", () -> Boolean.TRUE.equals(redisTemplate.opsForValue().setBit(formattedKey, offset, value)))
                    : Boolean.TRUE.equals(redisTemplate.opsForValue().setBit(formattedKey, offset, value));
        } catch (Exception e) {
            recordError("setBit", e);
            log.error("【Redis】SETBIT 操作失败 | key={} | offset={} | error={}", key, offset, e);
            return false;
        }
    }

    /**
     * 获取位图值
     *
     * @param key    键
     * @param offset 偏移量
     * @return 位图值
     */
    public boolean getBit(String key, long offset) {
        if (key == null) {
            return false;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("getBit", () -> Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(formattedKey, offset)))
                    : Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(formattedKey, offset));
        } catch (Exception e) {
            recordError("getBit", e);
            log.error("【Redis】GETBIT 操作失败 | key={} | offset={} | error={}", key, offset, e);
            return false;
        }
    }

    /**
     * 统计位图中值为 1 的位数
     *
     * @param key 键
     * @return 1 的位数
     */
    public long bitCount(String key) {
        if (key == null) {
            return 0;
        }
        String formattedKey = formatKey(key);
        try {
            return metricsCollector != null
                    ? metricsCollector.recordOperation("bitCount", () -> {
                        Long count = redisTemplate.execute((RedisCallback<Long>) connection ->
                                connection.stringCommands().bitCount(formattedKey.getBytes(StandardCharsets.UTF_8)));
                        return count != null ? count : 0L;
                    })
                    : Optional.ofNullable(redisTemplate.execute((RedisCallback<Long>) connection ->
                            connection.stringCommands().bitCount(formattedKey.getBytes(StandardCharsets.UTF_8)))).orElse(0L);
        } catch (Exception e) {
            recordError("bitCount", e);
            log.error("【Redis】BITCOUNT 操作失败 | key={} | error={}", key, e);
            return 0;
        }
    }

    // ============================ Lua 脚本操作 =============================

    /**
     * 执行 Lua 脚本（带 SHA 缓存优化）。
     *
     * <p>通过 {@link DefaultRedisScript} 执行脚本，Spring Data Redis 脚本执行器
     * 会先执行 {@code SCRIPT LOAD} 缓存脚本 SHA，后续调用自动使用 {@code EVALSHA}，
     * 避免每次请求都传输完整脚本内容，降低网络开销。
     *
     * @param script     Lua 脚本内容，不允许为 null
     * @param returnType 返回值类型
     * @param keys       键列表（自动添加统一前缀）
     * @param args       脚本参数
     * @param <T>        返回值类型
     * @return 脚本执行结果；脚本为空或执行异常时返回 null
     */
    public <T> T executeScriptWithShaCache(String script, Class<T> returnType,
                                           List<String> keys, Object... args) {
        if (script == null || script.isEmpty()) {
            return null;
        }
        try {
            List<String> formattedKeys = formatKeys(keys);
            DefaultRedisScript<T> redisScript = new DefaultRedisScript<>(script, returnType);
            return redisTemplate.execute(redisScript, formattedKeys, args);
        } catch (Exception e) {
            recordError("executeScript", e);
            log.error("【Redis】Lua 脚本执行失败 | error={}", e);
            return null;
        }
    }

    // ============================ 内部辅助方法 =============================

    /**
     * 释放分布式锁（校验锁持有者）
     *
     * <p>使用 Spring Data Redis 内置的 DefaultRedisScript，框架自动处理 EVALSHA 优化。
     *
     * @param lockKey   锁键
     * @param lockValue 锁值（UUID）
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
            redisTemplate.execute(script, Collections.singletonList(formatKey(lockKey)), lockValue);
        } catch (Exception e) {
            log.error("【Redis】释放锁失败 | lockKey={} | error={}", lockKey, e);
        }
    }

    /**
     * 为过期时间添加随机偏移，防止缓存雪崩
     *
     * @param baseSeconds 基础过期时间（秒）
     * @return 添加偏移后的过期时间
     */
    private long addJitter(long baseSeconds) {
        if (baseSeconds <= 0) {
            return baseSeconds;
        }
        long jitter = baseSeconds / CACHE_EXPIRE_JITTER_RATIO;
        if (jitter <= 0) {
            jitter = 1;
        }
        return baseSeconds + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
    }

    /**
     * 为 Duration 添加随机偏移
     *
     * @param base 基础过期时间
     * @return 添加偏移后的 Duration
     */
    private Duration addJitter(Duration base) {
        if (base == null || base.isZero() || base.isNegative()) {
            return base;
        }
        long baseSeconds = base.getSeconds();
        long jitteredSeconds = addJitter(baseSeconds);
        return Duration.ofSeconds(jitteredSeconds, base.getNano());
    }

    /**
     * 记录指标错误
     */
    private void recordError(String operationType, Throwable e) {
        if (metricsCollector != null) {
            metricsCollector.recordError(operationType, e);
        }
    }
}
