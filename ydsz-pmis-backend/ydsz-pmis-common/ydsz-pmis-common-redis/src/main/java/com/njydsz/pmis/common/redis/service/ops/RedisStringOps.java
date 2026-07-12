package com.njydsz.pmis.common.redis.service.ops;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.redis.config.RedisProperties;
import com.njydsz.pmis.common.redis.enums.RedisKeysEnum;
import com.njydsz.pmis.common.redis.metrics.RedisMetricsCollector;
import com.njydsz.pmis.common.util.collection.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Redis String / Bitmap 鎿嶄綔缁勪欢
 *
 * <p>浠?{@code RedisService} 鎸夋暟鎹被鍨嬫媶鍒嗚€屾潵锛岃亴璐ｅ崟涓€锛屼究浜庣淮鎶ゃ€?
 * 鍖呭惈锛氶€氱敤鎿嶄綔銆丼tring 鎿嶄綔銆丅itmap 鎿嶄綔銆?
 *
 * <p><b>澧炲己鐗规€э細</b>
 * <ul>
 *   <li>Lua 鑴氭湰淇濊瘉 getOrCompute 閿侀噴鏀剧殑鍘熷瓙鎬?/li>
 *   <li>Micrometer 鎸囨爣閲囬泦锛堝彲閫夛級</li>
 *   <li>杩囨湡鏃堕棿闅忔満鍋忕Щ闃叉缂撳瓨闆穿</li>
 *   <li>缁熶竴 Key 鍓嶇紑锛屾敮鎸佸搴旂敤鍏变韩 Redis</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
@Component
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

    /** Lua 鑴氭湰 SHA1 缂撳瓨锛岄伩鍏嶆瘡娆℃墽琛岄兘鍙戦€佸畬鏁磋剼鏈?*/
    private final ConcurrentHashMap<String, String> scriptShaCache = new ConcurrentHashMap<>();

    // ============================ 閫氱敤鎿嶄綔 =============================

    /**
     * 鏍煎紡鍖?Key锛屾坊鍔犵粺涓€鍓嶇紑
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

    @PostConstruct
    public void init() {
        Objects.requireNonNull(redisTemplate, "RedisTemplate 鏈纭垵濮嬪寲");
        Objects.requireNonNull(redisTemplate.getConnectionFactory(), "RedisConnectionFactory 鏈厤缃?);
        String prefix = redisProperties != null ? redisProperties.getKeyPrefix() : null;
        log.info("銆怰edis銆慠edisStringOps 鍒濆鍖栧畬鎴?| keyPrefix={}", prefix == null || prefix.isEmpty() ? "鏃? : prefix);
    }

    /**
     * 鎵归噺鏍煎紡鍖?Keys
     */
    private List<String> formatKeys(Collection<String> keys) {
        if (keys == null) {
            return Collections.emptyList();
        }
        return keys.stream().map(this::formatKey).collect(Collectors.toList());
    }

    /**
     * 璁剧疆閿殑杩囨湡鏃堕棿
     *
     * @param key  閿?
     * @param time 杩囨湡鏃堕棿锛堢锛?
     * @return true-璁剧疆鎴愬姛锛宖alse-璁剧疆澶辫触鎴栭敭涓嶅瓨鍦?
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
            log.error("銆怰edis銆戣缃繃鏈熸椂闂村け璐?| key={} | time={} | error={}", key, time, e);
            return false;
        }
    }

    /**
     * 璁剧疆閿殑杩囨湡鏃堕棿锛堜娇鐢?Duration锛?
     *
     * @param key     閿?
     * @param duration 杩囨湡鏃堕棿
     * @return true-璁剧疆鎴愬姛锛宖alse-璁剧疆澶辫触
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
            log.error("銆怰edis銆戣缃繃鏈熸椂闂村け璐?| key={} | duration={} | error={}", key, duration, e);
            return false;
        }
    }

    /**
     * 鑾峰彇閿殑杩囨湡鏃堕棿
     *
     * @param key 閿?
     * @return 杩囨湡鏃堕棿锛堢锛夛紝-1-姘镐箙鏈夋晥锛?2-閿笉瀛樺湪
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
            log.error("銆怰edis銆戣幏鍙栬繃鏈熸椂闂村け璐?| key={} | error={}", key, e);
            return -2;
        }
    }

    /**
     * 妫€鏌ラ敭鏄惁瀛樺湪
     *
     * @param key 閿?
     * @return true-瀛樺湪锛宖alse-涓嶅瓨鍦?
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
            log.error("銆怰edis銆戞鏌ラ敭鏄惁瀛樺湪澶辫触 | key={} | error={}", key, e);
            return false;
        }
    }

    /**
     * 鍒犻櫎閿?
     *
     * @param keys 閿暟缁?
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
            log.error("銆怰edis銆戝垹闄ら敭澶辫触 | keys={} | error={}", Arrays.toString(keys), e);
        }
    }

    /**
     * 鍒犻櫎閿紙闆嗗悎褰㈠紡锛?
     *
     * @param keys 閿泦鍚?
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
            log.error("銆怰edis銆戝垹闄ら敭澶辫触 | keys={} | error={}", keys, e);
        }
    }

    /**
     * 鎵归噺鍒犻櫎鍖归厤妯″紡鐨勯敭锛堜娇鐢?SCAN锛屽畨鍏級
     *
     * @param pattern 鍖归厤妯″紡锛屽 "user:*"
     * @return 鍒犻櫎鐨勯敭鏁伴噺
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
            log.error("銆怰edis銆戞壒閲忓垹闄ら敭澶辫触 | pattern={} | error={}", pattern, e);
            return 0;
        }
    }

    /**
     * 浣跨敤 SCAN 鍛戒护鎼滅储閿紙閬垮厤 KEYS 鍛戒护闃诲锛?
     *
     * <p>榛樿闄愬埗鏈€澶ц繑鍥炴暟閲忎负 10000锛岄槻姝㈠ぇ鏁版嵁閲忓満鏅?OOM銆?
     *
     * @param pattern 鍖归厤妯″紡
     * @return 鍖归厤鐨勯敭闆嗗悎
     */
    public Set<String> scan(String pattern) {
        return scan(pattern, 10000);
    }

    /**
     * 浣跨敤 SCAN 鍛戒护鎼滅储閿紙閬垮厤 KEYS 鍛戒护闃诲锛?
     *
     * @param pattern 鍖归厤妯″紡
     * @param maxKeys 鏈€澶ц繑鍥為敭鏁伴噺锛堥槻姝?OOM锛?
     * @return 鍖归厤鐨勯敭闆嗗悎
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
            log.error("銆怰edis銆慡CAN 鎿嶄綔澶辫触 | pattern={} | error={}", pattern, e);
            return Collections.emptySet();
        }
    }

    /**
     * 閲嶅懡鍚嶉敭
     *
     * @param oldKey 鏃ч敭鍚?
     * @param newKey 鏂伴敭鍚?
     * @return true-閲嶅懡鍚嶆垚鍔?
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
            log.error("銆怰edis銆戦噸鍛藉悕閿け璐?| oldKey={} | newKey={} | error={}", oldKey, newKey, e);
            return false;
        }
    }

    /**
     * 褰撴柊閿笉瀛樺湪鏃堕噸鍛藉悕
     *
     * @param oldKey 鏃ч敭鍚?
     * @param newKey 鏂伴敭鍚?
     * @return true-閲嶅懡鍚嶆垚鍔?
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
            log.error("銆怰edis銆戞潯浠堕噸鍛藉悕澶辫触 | oldKey={} | newKey={} | error={}", oldKey, newKey, e);
            return false;
        }
    }

    // ============================ String 鎿嶄綔 =============================

    /**
     * 鑾峰彇鍊?
     *
     * @param key 閿?
     * @return 鍊硷紝涓嶅瓨鍦ㄦ椂杩斿洖 null銆傚闇€绫诲瀷瀹夊叏杞崲锛岃浣跨敤 {@link #get(String, Class)}
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
            log.error("銆怰edis銆戣繛鎺ュけ璐ワ紝GET 鎿嶄綔闄嶇骇杩斿洖 null | key={} | error={}", key, e);
            return null;
        } catch (Exception e) {
            recordError("get", e);
            log.error("銆怰edis銆慓ET 鎿嶄綔澶辫触 | key={} | error={}", key, e);
            return null;
        }
    }

    /**
     * 鑾峰彇鍊硷紙甯︾被鍨嬭浆鎹級
     *
     * @param key   閿?
     * @param clazz 鐩爣绫诲瀷
     * @param <T>   鍊肩被鍨?
     * @return 鍊?
     */
    public <T> T get(String key, Class<T> clazz) {
        Objects.requireNonNull(clazz, "鐩爣绫诲瀷涓嶈兘涓?null");
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
            String json = JsonUtils.toJson(value);
            return JsonUtils.fromJson(json, clazz);
        } catch (Exception e) {
            log.error("銆怰edis銆戠被鍨嬭浆鎹㈠け璐?| key={} | targetClass={} | error={}", key, clazz.getName(), e);
            return null;
        }
    }

    /**
     * 璁剧疆鍊?
     *
     * @param key   閿?
     * @param value 鍊?
     * @return true-璁剧疆鎴愬姛
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
            log.error("銆怰edis銆戣繛鎺ュけ璐ワ紝SET 鎿嶄綔闄嶇骇杩斿洖 false | key={} | error={}", key, e);
            return false;
        } catch (Exception e) {
            recordError("set", e);
            log.error("銆怰edis銆慡ET 鎿嶄綔澶辫触 | key={} | error={}", key, e);
            return false;
        }
    }

    /**
     * 璁剧疆鍊硷紙甯﹁繃鏈熸椂闂达紝鑷姩娣诲姞闅忔満鍋忕Щ闃叉闆穿锛?
     *
     * @param key   閿?
     * @param value 鍊?
     * @param time  杩囨湡鏃堕棿锛堢锛?
     * @return true-璁剧疆鎴愬姛
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
            log.error("銆怰edis銆戣繛鎺ュけ璐ワ紝SET 鎿嶄綔闄嶇骇杩斿洖 false | key={} | time={} | error={}", key, time, e);
            return false;
        } catch (Exception e) {
            recordError("set", e);
            log.error("銆怰edis銆慡ET 鎿嶄綔澶辫触 | key={} | time={} | error={}", key, time, e);
            return false;
        }
    }

    /**
     * 璁剧疆鍊硷紙甯﹁繃鏈熸椂闂?Duration锛?
     *
     * @param key      閿?
     * @param value    鍊?
     * @param duration 杩囨湡鏃堕棿
     * @return true-璁剧疆鎴愬姛
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
            log.error("銆怰edis銆戣繛鎺ュけ璐ワ紝SET 鎿嶄綔闄嶇骇杩斿洖 false | key={} | duration={} | error={}", key, duration, e);
            return false;
        } catch (Exception e) {
            recordError("set", e);
            log.error("銆怰edis銆慡ET 鎿嶄綔澶辫触 | key={} | duration={} | error={}", key, duration, e);
            return false;
        }
    }

    /**
     * 鍙湁鍦ㄩ敭涓嶅瓨鍦ㄦ椂璁剧疆
     *
     * @param key    閿?
     * @param value  鍊?
     * @param expire 杩囨湡鏃堕棿锛堢锛?
     * @return true-璁剧疆鎴愬姛锛堥敭鍘熸湰涓嶅瓨鍦級
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
            log.error("銆怰edis銆慡ETNX 鎿嶄綔澶辫触 | key={} | expire={} | error={}", key, expire, e);
            return false;
        }
    }

    /**
     * 鍙湁鍦ㄩ敭瀛樺湪鏃惰缃?
     *
     * @param key    閿?
     * @param value  鍊?
     * @param expire 杩囨湡鏃堕棿锛堢锛?
     * @return true-璁剧疆鎴愬姛锛堥敭鍘熸湰瀛樺湪锛?
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
            log.error("銆怰edis銆慡ETXX 鎿嶄綔澶辫触 | key={} | expire={} | error={}", key, expire, e);
            return false;
        }
    }

    /**
     * 缂撳瓨绌块€忎繚鎶わ細鑾峰彇缂撳瓨锛岃嫢涓嶅瓨鍦ㄥ垯閫氳繃 supplier 鑾峰彇骞剁紦瀛?
     *
     * <p>浣跨敤 Lua 鑴氭湰淇濊瘉閿侀噴鏀剧殑鍘熷瓙鎬э紙鏍￠獙閿佹寔鏈夎€咃級锛岄槻姝㈣鍒犲叾浠栫嚎绋嬬殑閿併€?
     *
     * @param key      缂撳瓨閿?
     * @param expire   杩囨湡鏃堕棿锛堢锛?
     * @param supplier 鏁版嵁鎻愪緵鍑芥暟
     * @param clazz    鍊肩被鍨?
     * @param <T>      鍊肩被鍨?
     * @return 缂撳瓨鍊?
     */
    @SuppressWarnings("unchecked")
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
                        set(key, (T) NullPlaceholder.INSTANCE, Math.min(expire, 60));
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
                java.util.concurrent.locks.LockSupport.parkNanos(waitNanos);
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

    private static class NullPlaceholder {
        static final Object INSTANCE = new NullPlaceholder();
    }

    /**
     * 缂撳瓨绌块€忎繚鎶わ細鑾峰彇缂撳瓨锛岃嫢涓嶅瓨鍦ㄥ垯閫氳繃 supplier 鑾峰彇骞剁紦瀛橈紙浣跨敤鏋氫妇 Key锛?
     *
     * @param keyEnum  閿灇涓?
     * @param arg      閿弬鏁?
     * @param expire   杩囨湡鏃堕棿锛堢锛?
     * @param supplier 鏁版嵁鎻愪緵鍑芥暟
     * @param clazz    鍊肩被鍨?
     * @param <T>      鍊肩被鍨?
     * @return 缂撳瓨鍊?
     */
    public <T> T getOrCompute(RedisKeysEnum keyEnum, Object arg, long expire, Supplier<T> supplier, Class<T> clazz) {
        return getOrCompute(keyEnum.join(arg), expire, supplier, clazz);
    }

    /**
     * 閫掑鎿嶄綔
     *
     * @param key   閿?
     * @param delta 澧為噺锛堝繀椤诲ぇ浜?0锛?
     * @return 閫掑鍚庣殑鍊?
     */
    public long incr(String key, long delta) {
        if (key == null || delta <= 0) {
            throw new IllegalArgumentException("澧為噺蹇呴』澶т簬 0");
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
            log.error("銆怰edis銆慖NCR 鎿嶄綔澶辫触 | key={} | delta={} | error={}", key, delta, e);
            return 0;
        }
    }

    /**
     * 閫掑噺鎿嶄綔
     *
     * @param key   閿?
     * @param delta 鍑忛噺锛堝繀椤诲ぇ浜?0锛?
     * @return 閫掑噺鍚庣殑鍊?
     */
    public long decr(String key, long delta) {
        if (key == null || delta <= 0) {
            throw new IllegalArgumentException("鍑忛噺蹇呴』澶т簬 0");
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
            log.error("銆怰edis銆慏ECR 鎿嶄綔澶辫触 | key={} | delta={} | error={}", key, delta, e);
            return 0;
        }
    }

    /**
     * 鍘熷瓙閫掑锛堟诞鐐规暟锛?
     *
     * @param key   閿?
     * @param delta 澧為噺
     * @return 閫掑鍚庣殑鍊?
     */
    public double incrByFloat(String key, double delta) {
        if (key == null) {
            throw new IllegalArgumentException("閿笉鑳戒负绌?);
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
            log.error("銆怰edis銆慖NCRBYFLOAT 鎿嶄綔澶辫触 | key={} | delta={} | error={}", key, delta, e);
            return 0.0;
        }
    }

    /**
     * 鎵归噺鑾峰彇鍊?
     *
     * @param keys 閿泦鍚?
     * @return 鍊煎垪琛紙涓?keys 椤哄簭瀵瑰簲锛?
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
            log.error("銆怰edis銆慚GET 鎿嶄綔澶辫触 | keys={} | error={}", keys, e);
            return Collections.emptyList();
        }
    }

    /**
     * 鎵归噺鑾峰彇鍊硷紙娉涘瀷鐗堟湰锛?
     *
     * @param keys  閿泦鍚?
     * @param clazz 鍊肩被鍨?
     * @param <T>   鍊肩被鍨?
     * @return 鍊煎垪琛?
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
            log.error("銆怰edis銆慚GET 鎿嶄綔澶辫触 | keys={} | error={}", keys, e);
            return Collections.emptyList();
        }
    }

    // ============================ Bitmap 鎿嶄綔 =============================

    /**
     * 璁剧疆浣嶅浘
     *
     * @param key    閿?
     * @param offset 鍋忕Щ閲?
     * @param value  鍊硷紙true-1锛宖alse-0锛?
     * @return true-璁剧疆鎴愬姛
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
            log.error("銆怰edis銆慡ETBIT 鎿嶄綔澶辫触 | key={} | offset={} | error={}", key, offset, e);
            return false;
        }
    }

    /**
     * 鑾峰彇浣嶅浘鍊?
     *
     * @param key    閿?
     * @param offset 鍋忕Щ閲?
     * @return 浣嶅浘鍊?
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
            log.error("銆怰edis銆慓ETBIT 鎿嶄綔澶辫触 | key={} | offset={} | error={}", key, offset, e);
            return false;
        }
    }

    /**
     * 缁熻浣嶅浘涓€间负 1 鐨勪綅鏁?
     *
     * @param key 閿?
     * @return 1 鐨勪綅鏁?
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
            log.error("銆怰edis銆態ITCOUNT 鎿嶄綔澶辫触 | key={} | error={}", key, e);
            return 0;
        }
    }

    // ============================ 鍐呴儴杈呭姪鏂规硶 =============================

    /**
     * 浣跨敤 Lua 鑴氭湰瀹夊叏閲婃斁鍒嗗竷寮忛攣锛堟牎楠岄攣鎸佹湁鑰咃級
     *
     * <p>浣跨敤 EVALSHA 浼樺寲锛氫紭鍏堜娇鐢ㄧ紦瀛樼殑 SHA1 鎵ц鑴氭湰锛屽け璐ユ椂鍥為€€鍒?EVAL銆?
     *
     * @param lockKey   閿侀敭
     * @param lockValue 閿佸€硷紙UUID锛?
     */
    private void releaseLock(String lockKey, String lockValue) {
        try {
            executeScriptWithShaCache(UNLOCK_LUA, Long.class,
                    Collections.singletonList(formatKey(lockKey)), lockValue);
        } catch (Exception e) {
            log.error("銆怰edis銆戦噴鏀鹃攣澶辫触 | lockKey={} | error={}", lockKey, e);
        }
    }

    /**
     * 鎵ц Lua 鑴氭湰锛堝甫 EVALSHA 浼樺寲锛?
     *
     * <p>棣栨鎵ц浣跨敤 EVAL 鍙戦€佸畬鏁磋剼鏈紝骞剁紦瀛樿剼鏈殑 SHA1 鍊硷紱
     * 鍚庣画鎵ц浼樺厛浣跨敤 EVALSHA锛岃嫢 Redis 涓剼鏈凡涓㈠け锛堝閲嶅惎锛夊垯鍥為€€鍒?EVAL銆?
     *
     * @param script     Lua 鑴氭湰鍐呭
     * @param returnType 杩斿洖鍊肩被鍨?
     * @param keys       閿垪琛?
     * @param args       鍙傛暟鍒楄〃
     * @param <T>        杩斿洖鍊肩被鍨?
     * @return 鑴氭湰鎵ц缁撴灉
     */
    public <T> T executeScriptWithShaCache(String script, Class<T> returnType,
                                            List<String> keys, Object... args) {
        if (script == null) {
            return null;
        }
        try {
            String cachedSha1 = scriptShaCache.get(script);
            if (cachedSha1 != null) {
                // 浼樺厛浣跨敤 EVALSHA
                try {
                    DefaultRedisScript<T> evalShaScript = new DefaultRedisScript<>(script, returnType);
                    T result = redisTemplate.execute(evalShaScript, keys, args);
                    return result;
                } catch (Exception e) {
                    log.debug("銆怰edis銆慐VALSHA 鎵ц澶辫触锛屽洖閫€鍒?EVAL | sha1={} | error={}", cachedSha1, e);
                    scriptShaCache.remove(script);
                }
            }
            // 棣栨鎵ц鎴?EVALSHA 鍥為€€锛氫娇鐢?EVAL 鍙戦€佸畬鏁磋剼鏈?
            DefaultRedisScript<T> redisScript = new DefaultRedisScript<>(script, returnType);
            T result = redisTemplate.execute(redisScript, keys, args);
            // 缂撳瓨 SHA1
            String computedSha = computeSha1(script);
            if (computedSha != null) {
                scriptShaCache.put(script, computedSha);
            }
            return result;
        } catch (Exception e) {
            log.error("銆怰edis銆慙ua 鑴氭湰鎵ц澶辫触 | error={}", e);
            return null;
        }
    }

    /**
     * 璁＄畻 Lua 鑴氭湰鐨?SHA1 鍊?
     *
     * @param script Lua 鑴氭湰鍐呭
     * @return SHA1 鍗佸叚杩涘埗瀛楃涓?
     */
    private String computeSha1(String script) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(script.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.warn("銆怰edis銆慡HA1 绠楁硶涓嶅彲鐢紝璺宠繃 EVALSHA 缂撳瓨 | error={}", e);
            return null;
        }
    }

    /**
     * 涓鸿繃鏈熸椂闂存坊鍔犻殢鏈哄亸绉伙紝闃叉缂撳瓨闆穿
     *
     * @param baseSeconds 鍩虹杩囨湡鏃堕棿锛堢锛?
     * @return 娣诲姞鍋忕Щ鍚庣殑杩囨湡鏃堕棿
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
     * 涓?Duration 娣诲姞闅忔満鍋忕Щ
     *
     * @param base 鍩虹杩囨湡鏃堕棿
     * @return 娣诲姞鍋忕Щ鍚庣殑 Duration
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
     * 璁板綍鎸囨爣閿欒
     */
    private void recordError(String operationType, Throwable e) {
        if (metricsCollector != null) {
            metricsCollector.recordError(operationType, e);
        }
    }
}
