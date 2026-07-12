package com.njydsz.pmis.common.redis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis 服务门面类
 *
 * <p>作为统一入口聚合 Redis 操作，内部委托给 {@link RedisTemplate}。
 * 提供常用的 String、Hash、Set、List、ZSet 等操作方法，
 * 以及 Key 管理、Pipeline 等高级功能。
 *
 * <p>本类是对 {@link RedisTemplate} 的轻量封装，
 * 适用于其他 common 子模块（如 common-queue）复用 Redis 连接。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ============================ 通用操作 =============================

    /**
     * 获取底层 RedisTemplate（供子模块复用连接）
     *
     * @return RedisTemplate 实例
     */
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    public boolean expire(String key, long time) {
        return expire(key, Duration.ofSeconds(time));
    }

    public boolean expire(String key, Duration duration) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, duration));
        } catch (Exception e) {
            log.error("[Redis] expire 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key);
            return expire != null ? expire : -1;
        } catch (Exception e) {
            log.error("[Redis] getExpire 失败, key={}, error={}", key, e.getMessage());
            return -1;
        }
    }

    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("[Redis] hasKey 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public void del(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        try {
            redisTemplate.delete(Arrays.asList(keys));
        } catch (Exception e) {
            log.error("[Redis] del 失败, error={}", e.getMessage());
        }
    }

    public void del(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        try {
            redisTemplate.delete(keys);
        } catch (Exception e) {
            log.error("[Redis] del 失败, error={}", e.getMessage());
        }
    }

    public Set<String> scan(String pattern) {
        return scan(pattern, 100);
    }

    public Set<String> scan(String pattern, int maxKeys) {
        Set<String> keys = new LinkedHashSet<>();
        try {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                org.springframework.data.redis.core.Cursor<byte[]> cursor =
                        connection.keyCommands().scan(org.springframework.data.redis.core.ScanOptions
                                .scanOptions().match(pattern).count(maxKeys).build());
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[Redis] scan 失败, pattern={}, error={}", pattern, e.getMessage());
        }
        return keys;
    }

    // ============================ String 操作 =============================

    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("[Redis] get 失败, key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public boolean set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            log.error("[Redis] set 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public boolean set(String key, Object value, long time) {
        try {
            redisTemplate.opsForValue().set(key, value, time, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.error("[Redis] set 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public boolean set(String key, Object value, Duration duration) {
        try {
            redisTemplate.opsForValue().set(key, value, duration);
            return true;
        } catch (Exception e) {
            log.error("[Redis] set 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public boolean setIfAbsent(String key, Object value, long expire) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(key, value, expire, java.util.concurrent.TimeUnit.SECONDS));
        } catch (Exception e) {
            log.error("[Redis] setIfAbsent 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public long incr(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, delta);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] incr 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    public long decr(String key, long delta) {
        return incr(key, -delta);
    }

    // ============================ Hash 操作 =============================

    public boolean hSet(String key, String item, Object value) {
        try {
            redisTemplate.opsForHash().put(key, item, value);
            return true;
        } catch (Exception e) {
            log.error("[Redis] hSet 失败, key={}, item={}, error={}", key, item, e.getMessage());
            return false;
        }
    }

    public Object hGet(String key, String item) {
        try {
            return redisTemplate.opsForHash().get(key, item);
        } catch (Exception e) {
            log.error("[Redis] hGet 失败, key={}, item={}, error={}", key, item, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T hGet(String key, String item, Class<T> clazz) {
        Object value = hGet(key, item);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public Map<Object, Object> hGetAll(String key) {
        try {
            return redisTemplate.opsForHash().entries(key);
        } catch (Exception e) {
            log.error("[Redis] hGetAll 失败, key={}, error={}", key, e.getMessage());
            return Collections.emptyMap();
        }
    }

    public boolean hMSet(String key, Map<String, ?> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            return true;
        } catch (Exception e) {
            log.error("[Redis] hMSet 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public long hDel(String key, Object... items) {
        try {
            Long result = redisTemplate.opsForHash().delete(key, items);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] hDel 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    public boolean hHasKey(String key, String item) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, item));
        } catch (Exception e) {
            log.error("[Redis] hHasKey 失败, key={}, item={}, error={}", key, item, e.getMessage());
            return false;
        }
    }

    public long hIncr(String key, String item, long delta) {
        try {
            Long result = redisTemplate.opsForHash().increment(key, item, delta);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] hIncr 失败, key={}, item={}, error={}", key, item, e.getMessage());
            return 0;
        }
    }

    // ============================ Set 操作 =============================

    public long sAdd(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForSet().add(key, values);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] sAdd 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    public long sRem(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForSet().remove(key, values);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] sRem 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    public boolean sIsMember(String key, Object value) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, value));
        } catch (Exception e) {
            log.error("[Redis] sIsMember 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public long sSize(String key) {
        try {
            Long result = redisTemplate.opsForSet().size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] sSize 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Set<T> sMembers(String key, Class<T> clazz) {
        try {
            Set<Object> members = redisTemplate.opsForSet().members(key);
            if (members == null) {
                return Collections.emptySet();
            }
            return members.stream()
                    .filter(clazz::isInstance)
                    .map(o -> (T) o)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("[Redis] sMembers 失败, key={}, error={}", key, e.getMessage());
            return Collections.emptySet();
        }
    }

    // ============================ List 操作 =============================

    public long lPush(String key, Object value) {
        try {
            Long result = redisTemplate.opsForList().leftPush(key, value);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] lPush 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    public long rPush(String key, Object value) {
        try {
            Long result = redisTemplate.opsForList().rightPush(key, value);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] rPush 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    public Object lPop(String key) {
        try {
            return redisTemplate.opsForList().leftPop(key);
        } catch (Exception e) {
            log.error("[Redis] lPop 失败, key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T lPop(String key, Class<T> clazz) {
        Object value = lPop(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public Object rPop(String key) {
        try {
            return redisTemplate.opsForList().rightPop(key);
        } catch (Exception e) {
            log.error("[Redis] rPop 失败, key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T rPop(String key, Class<T> clazz) {
        Object value = rPop(key);
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    public long lSize(String key) {
        try {
            Long result = redisTemplate.opsForList().size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] lSize 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> lRange(String key, long start, long end, Class<T> clazz) {
        try {
            List<Object> range = redisTemplate.opsForList().range(key, start, end);
            if (range == null) {
                return Collections.emptyList();
            }
            return range.stream()
                    .filter(clazz::isInstance)
                    .map(o -> (T) o)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[Redis] lRange 失败, key={}, error={}", key, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ============================ ZSet 操作 =============================

    public boolean zAdd(String key, Object value, double score) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForZSet().add(key, value, score));
        } catch (Exception e) {
            log.error("[Redis] zAdd 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public long zRem(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForZSet().remove(key, values);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] zRem 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    public long zSize(String key) {
        try {
            Long result = redisTemplate.opsForZSet().size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] zSize 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    // ============================ Bitmap 操作 =============================

    public boolean setBit(String key, long offset, boolean value) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setBit(key, offset, value));
        } catch (Exception e) {
            log.error("[Redis] setBit 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public boolean getBit(String key, long offset) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().getBit(key, offset));
        } catch (Exception e) {
            log.error("[Redis] getBit 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    public long bitCount(String key) {
        try {
            Long result = redisTemplate.opsForValue().size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("[Redis] bitCount 失败, key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    // ============================ Pipeline 操作 =============================

    public List<Object> executePipelined(org.springframework.data.redis.core.RedisCallback<?> action) {
        try {
            return redisTemplate.executePipelined(action);
        } catch (Exception e) {
            log.error("[Redis] executePipelined 失败, error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ============================ Lua Script 操作 =========================

    /**
     * 执行 Lua 脚本并返回指定类型结果
     *
     * @param script     Lua 脚本内容
     * @param keys       Redis key 列表
     * @param returnType 返回值类型
     * @param args       脚本参数
     * @param <T>        返回值泛型
     * @return 脚本执行结果
     */
    public <T> T executeScript(String script, List<String> keys, Class<T> returnType, Object... args) {
        DefaultRedisScript<T> redisScript = new DefaultRedisScript<>(script, returnType);
        List<Object> serializedArgs = Arrays.asList(args);
        return redisTemplate.execute(redisScript, keys, serializedArgs.toArray());
    }

    /**
     * 执行 RedisScript 并返回结果
     *
     * @param script RedisScript 对象
     * @param keys   Redis key 列表
     * @param args   脚本参数
     * @param <T>    返回值泛型
     * @return 脚本执行结果
     */
    public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
        return redisTemplate.execute(script, keys, args);
    }
}
