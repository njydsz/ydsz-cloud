package com.njydsz.pmis.common.lock.impl;

import com.njydsz.pmis.common.lock.core.AbstractRedisDistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 鍙噸鍏ュ垎甯冨紡閿佸疄鐜?
 *
 * <p>鍩轰簬 Redis Hash 缁撴瀯瀹炵幇鍙噸鍏ヨ涔夛細
 * <ul>
 *   <li>Hash Key: lockKey</li>
 *   <li>Hash Field: clientId</li>
 *   <li>Hash Value: 閲嶅叆璁℃暟</li>
 * </ul>
 *
 * <p><b>鏍稿績鏈哄埗锛?/b>
 * <ul>
 *   <li>棣栨鑾峰彇閿侊細璁剧疆 Hash 瀛楁鍊间负 1</li>
 *   <li>閲嶅叆鑾峰彇锛氬師瀛愭€ч€掑璁℃暟</li>
 *   <li>閲婃斁閿侊細鍘熷瓙鎬ч€掑噺璁℃暟锛岃鏁颁负 0 鏃跺垹闄ゆ暣涓?Hash</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class RedisReentrantLock extends AbstractRedisDistributedLock {

    /**
     * 鑾峰彇鍙噸鍏ラ攣 Lua 鑴氭湰
     * <p>濡傛灉褰撳墠瀹㈡埛绔凡鎸佹湁閿佸垯閫掑閲嶅叆璁℃暟锛屽惁鍒欏湪鏃犲叾浠栨寔鏈夋椂鍒涘缓鏂伴攣
     */
    private static final String ACQUIRE_LOCK_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "if redis.call('HEXISTS', key, clientId) == 1 then " +
            "    redis.call('HINCRBY', key, clientId, 1) " +
            "    redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    return 1 " +
            "elseif redis.call('HLEN', key) == 0 then " +
            "    redis.call('HSET', key, clientId, 1) " +
            "    redis.call('HSET', key, '__remi_lease_ms__', leaseTimeMs) " +
            "    redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 閲婃斁鍙噸鍏ラ攣 Lua 鑴氭湰
     * <p>閫掑噺閲嶅叆璁℃暟锛岃鏁板綊闆舵椂鍒犻櫎鏁翠釜 Hash 閿?
     */
    private static final String RELEASE_LOCK_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "if redis.call('HEXISTS', key, clientId) == 0 then " +
            "    return 0 " +
            "end " +
            "local count = redis.call('HINCRBY', key, clientId, -1) " +
            "if count > 0 then " +
            "    local leaseTimeMs = redis.call('HGET', key, '__remi_lease_ms__') " +
            "    if leaseTimeMs then " +
            "        redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    end " +
            "    return 1 " +
            "else " +
            "    redis.call('HDEL', key, '__remi_lease_ms__') " +
            "    redis.call('DEL', key) " +
            "    return 1 " +
            "end";

    /**
     * 鑾峰彇閲嶅叆璁℃暟 Lua 鑴氭湰
     * <p>鏌ヨ褰撳墠瀹㈡埛绔湪鎸囧畾閿佷笂鐨勯噸鍏ヨ鏁?
     */
    private static final String GET_HOLD_COUNT_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local count = redis.call('HGET', key, clientId) " +
            "if count then " +
            "    return tonumber(count) " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 缁湡閿?Lua 鑴氭湰
     * <p>浠呭綋褰撳墠瀹㈡埛绔寔鏈夐攣鏃舵墠缁湡锛屽惁鍒欒繑鍥炲け璐?
     */
    private static final String RENEW_LOCK_LUA_SCRIPT =
            "local key = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "if redis.call('HEXISTS', key, clientId) == 1 then " +
            "    redis.call('PEXPIRE', key, leaseTimeMs) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 鑾峰彇閿佽剼鏈皝瑁?
     */
    private final DefaultRedisScript<Long> acquireLockScript;
    /**
     * 閲婃斁閿佽剼鏈皝瑁?
     */
    private final DefaultRedisScript<Long> releaseLockScript;
    /**
     * 鑾峰彇閲嶅叆璁℃暟鑴氭湰灏佽
     */
    private final DefaultRedisScript<Long> getHoldCountScript;
    /**
     * 缁湡閿佽剼鏈皝瑁?
     */
    private final DefaultRedisScript<Long> renewLockScript;

    /**
     * 鏋勯€犲彲閲嶅叆閿侊紙鏃犲懡鍚嶇┖闂达級
     *
     * @param stringRedisTemplate Redis 鎿嶄綔妯℃澘
     */
    public RedisReentrantLock(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, null);
    }

    /**
     * 鏋勯€犲彲閲嶅叆閿侊紙甯﹀懡鍚嶇┖闂达級
     *
     * @param stringRedisTemplate Redis 鎿嶄綔妯℃澘
     * @param namespace           閿侀敭鍛藉悕绌洪棿鍓嶇紑锛岀敤浜庡搴旂敤鍏变韩 Redis 鏃剁殑闅旂
     */
    public RedisReentrantLock(StringRedisTemplate stringRedisTemplate, String namespace) {
        super(stringRedisTemplate, namespace);
        this.acquireLockScript = new DefaultRedisScript<>(ACQUIRE_LOCK_LUA_SCRIPT, Long.class);
        this.releaseLockScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);
        this.getHoldCountScript = new DefaultRedisScript<>(GET_HOLD_COUNT_LUA_SCRIPT, Long.class);
        this.renewLockScript = new DefaultRedisScript<>(RENEW_LOCK_LUA_SCRIPT, Long.class);
    }

    @Override
    protected String doAcquireLock(String lockKey, String clientId, long leaseTime, TimeUnit timeUnit) {
        long leaseTimeMs = timeUnit.toMillis(leaseTime);
        try {
            Long result = stringRedisTemplate.execute(
                    acquireLockScript,
                    Collections.singletonList(lockKey),
                    clientId,
                    String.valueOf(leaseTimeMs)
            );
            boolean acquired = Long.valueOf(1L).equals(result);
            if (acquired) {
                log.debug("銆愬垎甯冨紡閿併€戣幏鍙栧彲閲嶅叆閿佹垚鍔?| lockKey={} | clientId={}", lockKey, clientId);
                recordLeaseTime(lockKey, leaseTimeMs);
                startWatchDog(lockKey, clientId, leaseTimeMs);
                return clientId;
            }
            return null;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戣幏鍙栧彲閲嶅叆閿佸紓甯?| lockKey={} | error={}", lockKey, e.getMessage(), e);
            return null;
        }
    }

    @Override
    protected boolean doReleaseLock(String lockKey, String clientId) {
        try {
            Long result = stringRedisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(lockKey),
                    clientId
            );
            boolean released = Long.valueOf(1L).equals(result);
            if (released) {
                log.debug("銆愬垎甯冨紡閿併€戦噴鏀惧彲閲嶅叆閿佹垚鍔?| lockKey={} | clientId={}", lockKey, clientId);
            }
            return released;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戦噴鏀惧彲閲嶅叆閿佸紓甯?| lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected boolean doIsLocked(String lockKey) {
        try {
            Long size = stringRedisTemplate.opsForHash().size(lockKey);
            return size != null && size > 0;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戞鏌ラ攣鐘舵€佸紓甯?| lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected long doGetRemainTime(String lockKey) {
        try {
            return stringRedisTemplate.getExpire(lockKey, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戣幏鍙栧墿浣欐椂闂村紓甯?| lockKey={} | error={}", lockKey, e.getMessage(), e);
            return -2;
        }
    }

    /**
     * 鑾峰彇褰撳墠瀹㈡埛绔湪鎸囧畾閿佷笂鐨勯噸鍏ヨ鏁?
     *
     * @param lockKey   閿佺殑閿?
     * @param lockValue 閿佺殑鍊硷紙瀹㈡埛绔爣璇嗭級
     * @return 閲嶅叆璁℃暟锛屾湭鎸佹湁閿佹椂杩斿洖 0
     */
    @Override
    public int getHoldCount(String lockKey, String lockValue) {
        try {
            Long result = stringRedisTemplate.execute(
                    getHoldCountScript,
                    Collections.singletonList(lockKey),
                    lockValue
            );
            return result != null ? result.intValue() : 0;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戣幏鍙栭噸鍏ヨ鏁板紓甯?| lockKey={} | error={}", lockKey, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 鍒ゆ柇鎸囧畾閿佹槸鍚︾敱褰撳墠绾跨▼鎸佹湁
     *
     * @param lockKey   閿佺殑閿?
     * @param lockValue 閿佺殑鍊硷紙瀹㈡埛绔爣璇嗭級
     * @return true-褰撳墠绾跨▼鎸佹湁璇ラ攣
     */
    @Override
    public boolean isHeldByCurrentThread(String lockKey, String lockValue) {
        return getHoldCount(lockKey, lockValue) > 0;
    }

    /**
     * 缁湡閿侊紝寤堕暱閿佺殑杩囨湡鏃堕棿
     *
     * <p>浠呭綋褰撳墠瀹㈡埛绔寔鏈夐攣鏃舵墠缁湡锛屽惁鍒欒繑鍥炲け璐ャ€?
     *
     * @param lockKey   閿佺殑閿?
     * @param lockValue 閿佺殑鍊硷紙瀹㈡埛绔爣璇嗭級
     * @param leaseTime 鏂扮殑绉熺害鏃堕棿
     * @param timeUnit  鏃堕棿鍗曚綅
     * @return true-缁湡鎴愬姛锛宖alse-缁湡澶辫触锛堥攣宸茶閲婃斁鎴栦笉灞炰簬褰撳墠瀹㈡埛绔級
     */
    public boolean renewLock(String lockKey, String lockValue, long leaseTime, TimeUnit timeUnit) {
        try {
            Long result = stringRedisTemplate.execute(
                    renewLockScript,
                    Collections.singletonList(lockKey),
                    lockValue,
                    String.valueOf(timeUnit.toMillis(leaseTime))
            );
            return Long.valueOf(1L).equals(result);
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戠画鏈熼攣寮傚父 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 灏濊瘯鑾峰彇閿侊紙涓嶇瓑寰咃級
     *
     * @param lockKey   閿佺殑閿?
     * @param leaseTime 绉熺害鏃堕棿
     * @param timeUnit  鏃堕棿鍗曚綅
     * @return 閿佸€硷紙瀹㈡埛绔爣璇嗭級锛岃幏鍙栧け璐ヨ繑鍥?null
     */
    @Override
    public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        String namespacedKey = buildNamespacedKey(lockKey);
        String clientId = getClientId(namespacedKey);
        String result = doAcquireLock(namespacedKey, clientId, leaseTime, timeUnit);
        if (result == null) {
            // 閿佽幏鍙栧け璐ユ椂娓呯悊 ThreadLocal锛岄槻姝㈡硠婕忥紙璋冪敤鏂逛笉浼氳皟鐢?unlock锛?
            clearClientId(namespacedKey);
            clearLeaseTime(namespacedKey);
        }
        return result;
    }

    /**
     * 灏濊瘯鑾峰彇閿侊紙甯︾瓑寰呮椂闂达級
     *
     * @param lockKey   閿佺殑閿?
     * @param waitTime  鏈€澶х瓑寰呮椂闂?
     * @param leaseTime 绉熺害鏃堕棿
     * @param timeUnit  鏃堕棿鍗曚綅
     * @return 閿佸€硷紙瀹㈡埛绔爣璇嗭級锛岃幏鍙栧け璐ヨ繑鍥?null
     * @throws InterruptedException 绛夊緟杩囩▼涓嚎绋嬭涓柇
     */
    @Override
    public String tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        String namespacedKey = buildNamespacedKey(lockKey);
        return tryLockWithWait(namespacedKey, waitTime, leaseTime, timeUnit);
    }

    /**
     * 璁剧疆閿殑杩囨湡鏃堕棿锛堟绉掔簿搴︼級
     *
     * @param key      Redis 閿?
     * @param time     杩囨湡鏃堕棿
     * @param unit     鏃堕棿鍗曚綅
     * @return 璁剧疆鎴愬姛杩斿洖杩囨湡鏃堕棿鐨勬绉掑€硷紝澶辫触杩斿洖 0
     */
    @Override
    public long pexpire(String key, long time, TimeUnit unit) {
        try {
            Boolean result = stringRedisTemplate.expire(key, Duration.ofMillis(unit.toMillis(time)));
            return Boolean.TRUE.equals(result) ? unit.toMillis(time) : 0;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€慞EXPIRE 缁湡寮傚父 | lockKey={} | error={}", key, e.getMessage(), e);
            return 0;
        }
    }
}
