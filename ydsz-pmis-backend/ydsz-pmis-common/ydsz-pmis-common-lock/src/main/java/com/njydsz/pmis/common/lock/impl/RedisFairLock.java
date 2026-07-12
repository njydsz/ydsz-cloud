package com.njydsz.pmis.common.lock.impl;

import com.njydsz.pmis.common.lock.core.AbstractRedisDistributedLock;
import com.njydsz.pmis.common.lock.core.DistributedLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 鍏钩鍒嗗竷寮忛攣瀹炵幇
 *
 * <p>鍩轰簬 Redis List 闃熷垪瀹炵幇鍏钩璋冨害锛屾寜瀹㈡埛绔姹傞『搴忚幏鍙栭攣锛堝厛鍒板厛寰楋級銆?
 * 鍐呴儴浣跨敤 Lua 鑴氭湰淇濊瘉鍏ラ槦銆佸嚭闃熴€侀攣鑾峰彇鐨勫師瀛愭€с€?
 *
 * <p><b>瀹炵幇鏈哄埗锛?/b>
 * <ul>
 *   <li>闃熷垪绠＄悊锛氶€氳繃 Redis List 缁存姢绛夊緟闃熷垪锛屾柊璇锋眰杩藉姞鍒伴槦灏?/li>
 *   <li>鍘熷瓙璋冨害锛歀ua 鑴氭湰妫€鏌ラ槦棣栧鎴风锛屼粎闃熼瀹㈡埛绔彲鑾峰彇閿?/li>
 *   <li>鍙噸鍏ユ敮鎸侊細鍚屼竴瀹㈡埛绔彲澶氭鑾峰彇閿侊紝鍐呴儴缁存姢閲嶅叆璁℃暟</li>
 * </ul>
 *
 * <p><b>閫傜敤鍦烘櫙锛?/b>闇€瑕佷弗鏍兼寜椤哄簭鎵ц鐨勫垎甯冨紡浠诲姟锛岄伩鍏嶉ゥ楗块棶棰樸€?
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see DistributedLock
 * @see RedisReentrantLock
 */
@Slf4j
public class RedisFairLock extends AbstractRedisDistributedLock {

    /**
     * 鑾峰彇鍏钩閿?Lua 鑴氭湰
     * <p>鏀寔鍙噸鍏ワ細褰撳墠瀹㈡埛绔凡鎸佹湁鏃堕€掑璁℃暟锛涘惁鍒欐鏌ョ瓑寰呴槦鍒楅槦棣栵紝浠呴槦棣栧鎴风鍙幏鍙栭攣
     * <p>鍏煎 Redis 6.0 浠ヤ笅鐗堟湰锛氫娇鐢?LINDEX 閬嶅巻鏇夸唬 LPOS 妫€鏌ラ槦鍒椾腑鏄惁瀛樺湪瀹㈡埛绔?
     */
    private static final String ACQUIRE_LOCK_LUA_SCRIPT =
            "local lockKey = KEYS[1] " +
            "local queueKey = KEYS[2] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "local function isInQueue(queueKey, clientId) " +
            "    local len = redis.call('LLEN', queueKey) " +
            "    for i = 0, len - 1, 1 do " +
            "        if redis.call('LINDEX', queueKey, i) == clientId then " +
            "            return true " +
            "        end " +
            "    end " +
            "    return false " +
            "end " +
            "if redis.call('HEXISTS', lockKey, 'owner') == 1 then " +
            "    if redis.call('HGET', lockKey, 'owner') == clientId then " +
            "        local count = redis.call('HINCRBY', lockKey, '__count', 1) " +
            "        redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "        return 1 " +
            "    else " +
            "        if not isInQueue(queueKey, clientId) then " +
            "            redis.call('RPUSH', queueKey, clientId) " +
            "        end " +
            "        return 0 " +
            "    end " +
            "end " +
            "local headClient = redis.call('LINDEX', queueKey, 0) " +
            "if headClient == false then " +
            "    redis.call('HSET', lockKey, 'owner', clientId) " +
            "    redis.call('HSET', lockKey, '__count', 1) " +
            "    redis.call('HSET', lockKey, '__leaseTime', leaseTimeMs) " +
            "    redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "    return 1 " +
            "end " +
            "if headClient == clientId then " +
            "    redis.call('HSET', lockKey, 'owner', clientId) " +
            "    redis.call('HSET', lockKey, '__count', 1) " +
            "    redis.call('HSET', lockKey, '__leaseTime', leaseTimeMs) " +
            "    redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "    redis.call('LPOP', queueKey) " +
            "    return 1 " +
            "end " +
            "if not isInQueue(queueKey, clientId) then " +
            "    redis.call('RPUSH', queueKey, clientId) " +
            "end " +
            "return 0";

    /**
     * 閲婃斁鍏钩閿?Lua 鑴氭湰
     * <p>閫掑噺閲嶅叆璁℃暟锛岃鏁板綊闆舵椂鍒犻櫎閿佸苟浠庣瓑寰呴槦鍒椾腑绉婚櫎瀹㈡埛绔?
     */
    private static final String RELEASE_LOCK_LUA_SCRIPT =
            "local lockKey = KEYS[1] " +
            "local queueKey = KEYS[2] " +
            "local clientId = ARGV[1] " +
            "local owner = redis.call('HGET', lockKey, 'owner') " +
            "if owner == clientId then " +
            "    local count = redis.call('HGET', lockKey, '__count') " +
            "    if count and tonumber(count) > 1 then " +
            "        redis.call('HINCRBY', lockKey, '__count', -1) " +
            "        local leaseTimeMs = redis.call('HGET', lockKey, '__leaseTime') " +
            "        if leaseTimeMs then " +
            "            redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "        end " +
            "        return 1 " +
            "    else " +
            "        redis.call('DEL', lockKey) " +
            "        redis.call('LREM', queueKey, 1, clientId) " +
            "        return 1 " +
            "    end " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 缁湡鍏钩閿?Lua 鑴氭湰
     * <p>浠呭綋褰撳墠瀹㈡埛绔槸閿佺殑鎸佹湁鑰呮椂鎵嶇画鏈?
     */
    private static final String RENEW_LOCK_LUA_SCRIPT =
            "local lockKey = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local leaseTimeMs = ARGV[2] " +
            "if redis.call('HGET', lockKey, 'owner') == clientId then " +
            "    redis.call('PEXPIRE', lockKey, leaseTimeMs) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

    /**
     * 绛夊緟闃熷垪榛樿杩囨湡鏃堕棿锛堢锛?
     */
    private static final long QUEUE_EXPIRE_SECONDS = 3600;

    /**
     * 娓呯悊绛夊緟闃熷垪涓寚瀹氬鎴风 Lua 鑴氭湰
     * <p>浠庨槦鍒椾腑绉婚櫎 clientId锛屽苟璁剧疆闃熷垪 TTL 闃叉瀛ょ珛闃熷垪
     */
    private static final String CLEANUP_QUEUE_LUA_SCRIPT =
            "local queueKey = KEYS[1] " +
            "local clientId = ARGV[1] " +
            "local queueTtlSeconds = ARGV[2] " +
            "redis.call('LREM', queueKey, 1, clientId) " +
            "if redis.call('LLEN', queueKey) == 0 then " +
            "    redis.call('DEL', queueKey) " +
            "else " +
            "    redis.call('EXPIRE', queueKey, queueTtlSeconds) " +
            "end " +
            "return 1";

    /**
     * 鑾峰彇閿佽剼鏈皝瑁?
     */
    private final DefaultRedisScript<Long> acquireLockScript;
    /**
     * 閲婃斁閿佽剼鏈皝瑁?
     */
    private final DefaultRedisScript<Long> releaseLockScript;
    /**
     * 缁湡閿佽剼鏈皝瑁?
     */
    private final DefaultRedisScript<Long> renewLockScript;
    /**
     * 娓呯悊闃熷垪鑴氭湰灏佽
     */
    private final DefaultRedisScript<Long> cleanupQueueScript;

    /**
     * 鏋勯€犲叕骞抽攣锛堟棤鍛藉悕绌洪棿锛?
     *
     * @param stringRedisTemplate Redis 鎿嶄綔妯℃澘
     */
    public RedisFairLock(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, null);
    }

    /**
     * 鏋勯€犲叕骞抽攣锛堝甫鍛藉悕绌洪棿锛?
     *
     * @param stringRedisTemplate Redis 鎿嶄綔妯℃澘
     * @param namespace           閿侀敭鍛藉悕绌洪棿鍓嶇紑锛岀敤浜庡搴旂敤鍏变韩 Redis 鏃剁殑闅旂
     */
    public RedisFairLock(StringRedisTemplate stringRedisTemplate, String namespace) {
        super(stringRedisTemplate, namespace);
        this.acquireLockScript = new DefaultRedisScript<>(ACQUIRE_LOCK_LUA_SCRIPT, Long.class);
        this.releaseLockScript = new DefaultRedisScript<>(RELEASE_LOCK_LUA_SCRIPT, Long.class);
        this.renewLockScript = new DefaultRedisScript<>(RENEW_LOCK_LUA_SCRIPT, Long.class);
        this.cleanupQueueScript = new DefaultRedisScript<>(CLEANUP_QUEUE_LUA_SCRIPT, Long.class);
    }

    /**
     * 鑾峰彇鍏钩閿佺瓑寰呴槦鍒楃殑 Redis Key
     *
     * @param lockKey 閿佺殑閿?
     * @return 绛夊緟闃熷垪閿?
     */
    private String getQueueKey(String lockKey) {
        return lockKey + ":fair:queue";
    }

    /**
     * 灏濊瘯鑾峰彇鍏钩閿侊紙涓嶇瓑寰咃級
     *
     * <p>鎸夌瓑寰呴槦鍒楅『搴忚幏鍙栭攣锛屽綋鍓嶅鎴风鍦ㄩ槦棣栨垨閿佺┖闂叉椂鍙幏鍙栥€?
     *
     * @param lockKey   閿佺殑閿?
     * @param leaseTime 绉熺害鏃堕棿
     * @param timeUnit  鏃堕棿鍗曚綅
     * @return 閿佸€硷紙瀹㈡埛绔爣璇嗭級锛岃幏鍙栧け璐ヨ繑鍥?null
     */
    @Override
    public String tryLock(String lockKey, long leaseTime, TimeUnit timeUnit) {
        String namespacedKey = buildNamespacedKey(lockKey);
        long leaseTimeMs = timeUnit.toMillis(leaseTime);
        String clientId = getClientId(namespacedKey);
        String queueKey = getQueueKey(namespacedKey);
        boolean acquired = false;
        try {
            stringRedisTemplate.expire(queueKey, Duration.ofSeconds(QUEUE_EXPIRE_SECONDS));
            Long result = stringRedisTemplate.execute(
                    acquireLockScript,
                    Arrays.asList(namespacedKey, queueKey),
                    clientId,
                    String.valueOf(leaseTimeMs)
            );
            acquired = Long.valueOf(1L).equals(result);
            if (acquired) {
                log.debug("銆愬垎甯冨紡閿併€戣幏鍙栧叕骞抽攣鎴愬姛 | lockKey={} | clientId={}", lockKey, clientId);
                recordLeaseTime(namespacedKey, leaseTimeMs);
                startWatchDog(namespacedKey, clientId, leaseTimeMs);
                return clientId;
            }
            return null;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戣幏鍙栧叕骞抽攣寮傚父 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return null;
        } finally {
            // 閿佽幏鍙栧け璐ユ椂娓呯悊 ThreadLocal 鍜岀瓑寰呴槦鍒楋紝闃叉娉勬紡锛堣皟鐢ㄦ柟涓嶄細璋冪敤 unlock锛?
            if (!acquired) {
                clearClientId(namespacedKey);
                clearLeaseTime(namespacedKey);
                cleanupQueue(queueKey, clientId);
            }
        }
    }

    /**
     * 灏濊瘯鑾峰彇鍏钩閿侊紙甯︾瓑寰呮椂闂达級
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

    @Override
    protected String doAcquireLock(String lockKey, String clientId, long leaseTime, TimeUnit timeUnit) {
        return tryLock(lockKey, leaseTime, timeUnit);
    }

    @Override
    protected boolean doReleaseLock(String lockKey, String clientId) {
        String queueKey = getQueueKey(lockKey);
        try {
            Long result = stringRedisTemplate.execute(
                    releaseLockScript,
                    Arrays.asList(lockKey, queueKey),
                    clientId
            );
            boolean released = Long.valueOf(1L).equals(result);
            if (released) {
                log.debug("銆愬垎甯冨紡閿併€戦噴鏀惧叕骞抽攣鎴愬姛 | lockKey={} | clientId={}", lockKey, clientId);
            }
            return released;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戦噴鏀惧叕骞抽攣寮傚父 | lockKey={} | error={}", lockKey, e.getMessage(), e);
            return false;
        }
    }

    @Override
    protected boolean doIsLocked(String lockKey) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey));
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
     * 缁湡鍏钩閿侊紝寤堕暱閿佺殑杩囨湡鏃堕棿
     *
     * <p>浠呭綋褰撳墠瀹㈡埛绔槸閿佺殑鎸佹湁鑰呮椂鎵嶇画鏈燂紝鍚﹀垯杩斿洖澶辫触銆?
     *
     * @param lockKey   閿佺殑閿?
     * @param lockValue 閿佺殑鍊硷紙瀹㈡埛绔爣璇嗭級
     * @param leaseTime 鏂扮殑绉熺害鏃堕棿
     * @param timeUnit  鏃堕棿鍗曚綅
     * @return true-缁湡鎴愬姛锛宖alse-缁湡澶辫触
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
     * 娓呯悊绛夊緟闃熷垪涓殑鎸囧畾瀹㈡埛绔?
     * <p>鍦ㄨ幏鍙栭攣澶辫触鎴栬秴鏃舵椂璋冪敤锛岄槻姝㈠鎴风閬楃暀鍦ㄩ槦鍒椾腑
     *
     * @param queueKey 闃熷垪閿?
     * @param clientId 瀹㈡埛绔爣璇?
     */
    private void cleanupQueue(String queueKey, String clientId) {
        try {
            stringRedisTemplate.execute(
                    cleanupQueueScript,
                    Collections.singletonList(queueKey),
                    clientId,
                    String.valueOf(QUEUE_EXPIRE_SECONDS)
            );
            log.debug("銆愬垎甯冨紡閿併€戝叕骞抽攣绛夊緟闃熷垪娓呯悊 | queueKey={} | clientId={}", queueKey, clientId);
        } catch (Exception e) {
            log.debug("銆愬垎甯冨紡閿併€戞竻鐞嗙瓑寰呴槦鍒楀紓甯?| queueKey={} | error={}", queueKey, e.getMessage());
        }
    }

    @Override
    public int getQueuePosition(String lockKey, String lockValue) {
        String queueKey = getQueueKey(lockKey);
        try {
            Long index = stringRedisTemplate.opsForList().indexOf(queueKey, lockValue);
            return index != null ? index.intValue() : -1;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戣幏鍙栨帓闃熶綅缃紓甯?| lockKey={} | error={}", lockKey, e.getMessage(), e);
            return -1;
        }
    }

    @Override
    public int getQueueSize(String lockKey) {
        String queueKey = getQueueKey(lockKey);
        try {
            Long size = stringRedisTemplate.opsForList().size(queueKey);
            return size != null ? size.intValue() : -1;
        } catch (Exception e) {
            log.error("銆愬垎甯冨紡閿併€戣幏鍙栨帓闃熷ぇ灏忓紓甯?| lockKey={} | error={}", lockKey, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 璁剧疆閿殑杩囨湡鏃堕棿锛堟绉掔簿搴︼級
     *
     * @param key  Redis 閿?
     * @param time 杩囨湡鏃堕棿
     * @param unit 鏃堕棿鍗曚綅
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
