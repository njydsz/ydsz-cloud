package com.njydsz.pmis.common.redis.service;

import com.njydsz.pmis.common.redis.service.ops.RedisStringOps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Redis 缂撳瓨闃叉姢宸ュ叿绫? *
 * <p>鎻愪緵涓夊ぇ缂撳瓨淇濇姢绛栫暐锛?/p>
 * <ul>
 *   <li><b>闃茬┛閫?/b>锛氬竷闅嗚繃婊ゅ櫒妯″紡锛屽涓嶅瓨鍦ㄧ殑鏁版嵁杩涜绌哄€肩紦瀛橈紝闃叉澶ч噺涓嶅瓨鍦ㄧ殑 key 鎵撳埌鏁版嵁搴?/li>
 *   <li><b>闃插嚮绌?/b>锛氬垎甯冨紡閿佹ā寮忥紝瀵圭儹鐐?key 鍦ㄧ紦瀛樺け鏁堟椂鍙厑璁镐竴涓嚎绋嬪洖婧愶紝闃叉鐬椂楂樺苟鍙戞墦鍒版暟鎹簱</li>
 *   <li><b>闃查洩宕?/b>锛氶殢鏈鸿繃鏈熸椂闂达紝鍦ㄥ熀纭€ TTL 涓婂彔鍔犻殢鏈烘壈鍔紝闃叉澶ч噺缂撳瓨鍚屾椂澶辨晥</li>
 * </ul>
 *
 * <p><b>浣跨敤绀轰緥锛?/b></p>
 * <pre>{@code
 * // 闃茬┛閫?- 缂撳瓨绌哄€? * User user = cacheGuard.antiPenetration(
 *     "user:" + id,
 *     () -> userMapper.selectById(id),
 *     User.class
 * );
 *
 * // 闃插嚮绌?- 鍒嗗竷寮忛攣淇濇姢鐑偣 key
 * Product product = cacheGuard.antiBreakdown(
 *     "product:hot:" + id,
 *     300,
 *     () -> productService.getById(id),
 *     Product.class
 * );
 *
 * // 闃查洩宕?- 闅忔満 TTL
 * List<Order> orders = cacheGuard.antiAvalanche(
 *     "user:orders:" + userId,
 *     600,
 *     300000,  // 鏈€澶氶澶?5 鍒嗛挓闅忔満
 *     () -> orderMapper.selectByUserId(userId),
 *     List.class
 * );
 * }</pre>
 *
 * <p><b>闃插嚮绌块攣瀹炵幇璇存槑锛?/b></p>
 * <p>鐢变簬 {@code ydsz-pmis-common-lock} 妯″潡宸蹭緷璧?{@code ydsz-pmis-common-redis}锛屽弽鍚戜緷璧栦細褰㈡垚寰幆渚濊禆锛? * 鍥犳鏈被鍐呭祵瀹炵幇 WatchDog 缁湡鏈哄埗锛堜笌 {@code LockWatchDog} 鐩稿悓鐨勮璁℃ā寮忥級锛? * 纭繚閿佸湪缂撳瓨閲嶅缓鏈熼棿涓嶄細鍥犱笟鍔℃墽琛屾椂闂磋秴杩?leaseTime 鑰岃嚜鍔ㄩ噴鏀俱€?/p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class RedisCacheGuard {

    private static final String NULL_PLACEHOLDER = "__NULL__";
    private static final String PENETRATION_LOCK_PREFIX = "cache:guard:penetration:";
    private static final String BREAKDOWN_LOCK_PREFIX = "cache:guard:breakdown:";

    private static final String RELEASE_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    /**
     * 缁湡閿?Lua 鑴氭湰锛氫粎褰撻攣鎸佹湁鑰呭尮閰嶆椂鎵嶇画鏈燂紝閬垮厤璇画鏈熶粬浜烘寔鏈夌殑閿?     */
    private static final String RENEW_LOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";

    /**
     * WatchDog 鏈€澶х画鏈熸鏁帮紙涓?ydsz-pmis-common-lock 鐨?LockWatchDog 榛樿鍊间竴鑷达紝绾?30 鍒嗛挓锛?     */
    private static final int MAX_RENEW_TIMES = 100;

    /** 鑷棆绛夊緟鏈€澶ф椂闀匡紙姣锛夛紝绛夊緟鎸侀攣绾跨▼鍥炲～缂撳瓨 */
    private static final long SPIN_MAX_WAIT_MS = 3000;
    /** 鑷棆绛夊緟鍒濆閫€閬块棿闅旓紙姣锛?*/
    private static final long SPIN_INITIAL_BACKOFF_MS = 20;
    /** 鑷棆绛夊緟鏈€澶ч€€閬块棿闅旓紙姣锛?*/
    private static final long SPIN_MAX_BACKOFF_MS = 500;
    /** 闃插嚮绌块攣绛夊緟鑾峰彇鏈€澶ф椂闀匡紙姣锛?*/
    private static final long LOCK_WAIT_MS = 2000;
    /** 闃插嚮绌块攣绛夊緟鑾峰彇鍒濆閫€閬块棿闅旓紙姣锛?*/
    private static final long LOCK_WAIT_INITIAL_BACKOFF_MS = 10;
    /** 闃插嚮绌块攣绛夊緟鑾峰彇鏈€澶ч€€閬块棿闅旓紙姣锛?*/
    private static final long LOCK_WAIT_MAX_BACKOFF_MS = 200;
    /** 闃茬┛閫忛攣绉熺害鏃堕棿锛堢锛?*/
    private static final int PENETRATION_LOCK_LEASE_SECONDS = 5;
    /** 闃插嚮绌块攣绉熺害鏃堕棿锛堢锛?*/
    private static final int BREAKDOWN_LOCK_LEASE_SECONDS = 10;
    /** 浼橀泤鍏抽棴鏃剁瓑寰呰皟搴﹀櫒缁堟鐨勬渶澶ф椂闀匡紙绉掞級 */
    private static final long SHUTDOWN_AWAIT_SECONDS = 5;

    private final RedisService redisService;
    private final RedisStringOps stringOps;
    private final RedisTemplate<String, Object> redisTemplate;
    private final int nullValueTtlSeconds;

    /**
     * WatchDog 缁湡璋冨害鍣紙瀹堟姢绾跨▼姹狅級锛岀敤浜庨槻鍑荤┛閿佺殑鑷姩缁湡
     */
    private final ScheduledExecutorService watchDogScheduler;

    /**
     * 娲昏穬鐨?WatchDog 缁湡浠诲姟锛宬ey 涓洪攣閿?     */
    private final ConcurrentHashMap<String, WatchTask> activeWatchTasks = new ConcurrentHashMap<>();

    /**
     * WatchDog 缁湡浠诲姟涓婁笅鏂?     */
    private static class WatchTask {
        final String lockKey;
        final String lockValue;
        final long leaseTimeMs;
        final AtomicBoolean running;
        final ScheduledFuture<?> future;
        volatile int renewCount;

        WatchTask(String lockKey, String lockValue, long leaseTimeMs,
                  AtomicBoolean running, ScheduledFuture<?> future) {
            this.lockKey = lockKey;
            this.lockValue = lockValue;
            this.leaseTimeMs = leaseTimeMs;
            this.running = running;
            this.future = future;
            this.renewCount = 0;
        }
    }

    public RedisCacheGuard(RedisService redisService) {
        this(redisService, 1800);
    }

    public RedisCacheGuard(RedisService redisService, int nullValueTtlSeconds) {
        this.redisService = redisService;
        this.stringOps = redisService.stringOps();
        this.redisTemplate = redisService.getRedisTemplate();
        this.nullValueTtlSeconds = nullValueTtlSeconds;
        this.watchDogScheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "remi-cache-guard-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 浼橀泤鍏抽棴锛氬仠姝㈡墍鏈?WatchDog 缁湡浠诲姟骞跺叧闂皟搴﹀櫒
     *
     * <p>鐢?Spring 瀹瑰櫒鍦ㄩ攢姣佹椂鑷姩璋冪敤锛堝綋 RedisCacheGuard 浣滀负 Bean 娉ㄥ唽鏃讹級銆?     * 瀵逛簬鎵嬪姩 new 鐨勫満鏅紝璋冪敤鏂瑰簲涓诲姩璋冪敤 {@link #shutdown()}銆?/p>
     */
    @PreDestroy
    public void shutdown() {
        // 鍏堝仠姝㈡墍鏈夋椿璺冪殑缁湡浠诲姟
        for (Map.Entry<String, WatchTask> entry : activeWatchTasks.entrySet()) {
            WatchTask task = entry.getValue();
            task.running.set(false);
            task.future.cancel(false);
            log.debug("銆怰edisCacheGuard銆戝叧闂?WatchDog 缁湡浠诲姟 | key={}", entry.getKey());
        }
        activeWatchTasks.clear();

        // 鍏抽棴璋冨害鍣ㄥ苟绛夊緟姝ｅ湪鎵ц鐨勪换鍔″畬鎴?        watchDogScheduler.shutdown();
        try {
            if (!watchDogScheduler.awaitTermination(SHUTDOWN_AWAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("銆怰edisCacheGuard銆慦atchDog 璋冨害鍣ㄥ湪 {}s 鍐呮湭缁堟锛屾墽琛屽己鍒跺叧闂?, SHUTDOWN_AWAIT_SECONDS);
                watchDogScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            watchDogScheduler.shutdownNow();
        }
        log.info("銆怰edisCacheGuard銆慦atchDog 璋冨害鍣ㄥ凡鍏抽棴");
    }

    /**
     * 闃茬紦瀛樼┛閫?- 甯冮殕杩囨护鍣ㄦā寮忥紙绌哄€肩紦瀛橈級
     *
     * <p>褰撴煡璇㈢粨鏋滀负 null 鏃讹紝鍐欏叆涓€涓壒娈婄殑绌哄€兼爣璁板埌缂撳瓨涓紝
     * 璁剧疆杈冪煭鐨勮繃鏈熸椂闂淬€傚悗缁浉鍚?key 鐨勬煡璇㈢洿鎺ヨ繑鍥?null锛?     * 閬垮厤澶ч噺涓嶅瓨鍦ㄧ殑 key 鍙嶅鎵撳埌鏁版嵁搴撱€?/p>
     *
     * @param key      缂撳瓨 key
     * @param supplier 鍥炴簮鍔犺浇鏁版嵁鐨勯€昏緫锛堟煡璇㈡暟鎹簱绛夛級
     * @param clazz    杩斿洖鍊肩被鍨?     * @param <T>      鏁版嵁绫诲瀷
     * @return 鏌ヨ缁撴灉锛屼笉瀛樺湪鏃惰繑鍥?null
     */
    public <T> T antiPenetration(String key, Supplier<T> supplier, Class<T> clazz) {
        return antiPenetration(key, supplier, clazz, 60);
    }

    /**
     * 闃茬紦瀛樼┛閫?- 甯冮殕杩囨护鍣ㄦā寮忥紙绌哄€肩紦瀛橈級
     *
     * @param key           缂撳瓨 key
     * @param supplier      鍥炴簮鍔犺浇鏁版嵁鐨勯€昏緫
     * @param clazz         杩斿洖鍊肩被鍨?     * @param nullCacheSec  绌哄€肩紦瀛樻椂闀匡紙绉掞級锛岄粯璁?60 绉?     * @param <T>           鏁版嵁绫诲瀷
     * @return 鏌ヨ缁撴灉锛屼笉瀛樺湪鏃惰繑鍥?null
     */
    public <T> T antiPenetration(String key, Supplier<T> supplier, Class<T> clazz, int nullCacheSec) {
        // 鍏堝皾璇曚粠缂撳瓨鑾峰彇
        Object cached = stringOps.get(key);
        if (cached != null) {
            if (NULL_PLACEHOLDER.equals(cached)) {
                log.debug("銆怰edisCacheGuard銆戝懡涓┖鍊肩紦瀛?| key={}", key);
                return null;
            }
            if (clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
            return stringOps.get(key, clazz);
        }

        // 缂撳瓨鏈懡涓紝浣跨敤鍒嗗竷寮忛攣闃叉骞跺彂绌块€?        String lockKey = PENETRATION_LOCK_PREFIX + key;
        String lockValue = null;
        try {
            lockValue = acquireLock(lockKey, PENETRATION_LOCK_LEASE_SECONDS);
            if (lockValue != null) {
                // 鑾峰彇閿佹垚鍔燂紝鍙岄噸妫€鏌ョ紦瀛?                cached = stringOps.get(key);
                if (cached != null) {
                    return NULL_PLACEHOLDER.equals(cached) ? null : stringOps.get(key, clazz);
                }

                // 鍥炴簮鏌ヨ鏁版嵁搴?                T data = supplier.get();
                if (data == null) {
                    stringOps.set(key, NULL_PLACEHOLDER, nullCacheSec);
                    log.info("銆怰edisCacheGuard銆戣缃┖鍊肩紦瀛?| key={} | ttl={}s", key, nullCacheSec);
                    return null;
                } else {
                    stringOps.set(key, data, nullValueTtlSeconds);
                    return data;
                }
            } else {
                // 鏈幏鍙栧埌閿侊紝鑷棆绛夊緟缂撳瓨灏辩华锛堟寚鏁伴€€閬匡級锛屼笌闃插嚮绌夸繚鎸佷竴鑷?                return spinWaitForCacheOrPenetration(key, supplier, clazz, nullCacheSec);
            }
        } finally {
            if (lockValue != null) {
                releaseLock(lockKey, lockValue);
            }
        }
    }

    /**
     * 闃茬紦瀛樺嚮绌?- 鍒嗗竷寮忛攣妯″紡锛堝甫 WatchDog 缁湡 + singleflight锛?     *
     * <p>閽堝鐑偣 key 鍦ㄧ紦瀛樺け鏁堢灛闂达紝澶ч噺璇锋眰鍚屾椂鎵撳埌鏁版嵁搴撶殑闂銆?     * 浣跨敤甯?WatchDog 缁湡鐨勫垎甯冨紡閿佺‘淇濆悓涓€鏃跺埢鍙湁涓€涓嚎绋嬪洖婧愬姞杞芥暟鎹紝
     * 閿佸湪缂撳瓨閲嶅缓鏈熼棿涓嶄細鍥犱笟鍔℃墽琛屾椂闂磋秴杩?leaseTime 鑰岃嚜鍔ㄩ噴鏀俱€?/p>
     *
     * <p><b>singleflight 鏈哄埗锛?/b>绛夊緟閿佺殑绾跨▼鍦ㄨ幏鍙栧埌閿佸悗锛屼細鍏堟鏌ョ紦瀛樻槸鍚﹀凡琚?     * 绗竴涓嚎绋嬪～鍏咃紝濡傚凡琚～鍏呭垯鐩存帴杩斿洖缂撳瓨鍊硷紝涓嶅啀閲嶅缓锛岄伩鍏嶉噸澶嶅洖婧愩€?/p>
     *
     * @param key      缂撳瓨 key
     * @param expire   缂撳瓨杩囨湡鏃堕棿锛堢锛?     * @param supplier 鍥炴簮鍔犺浇鏁版嵁鐨勯€昏緫
     * @param clazz    杩斿洖鍊肩被鍨?     * @param <T>      鏁版嵁绫诲瀷
     * @return 鏌ヨ缁撴灉
     */
    public <T> T antiBreakdown(String key, long expire, Supplier<T> supplier, Class<T> clazz) {
        // 鍏堝皾璇曚粠缂撳瓨鑾峰彇
        Object cached = stringOps.get(key);
        if (cached != null) {
            if (clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
            return stringOps.get(key, clazz);
        }

        // 缂撳瓨澶辨晥锛屼娇鐢ㄥ垎甯冨紡閿佷繚鎶わ紙甯?WatchDog 缁湡 + 绛夊緟閲嶈瘯锛屽疄鐜?singleflight锛?        String lockKey = BREAKDOWN_LOCK_PREFIX + key;
        String lockValue = null;
        try {
            // 灏濊瘯鑾峰彇閿侊紙甯︾瓑寰咃級锛岃幏鍙栧悗 WatchDog 鑷姩缁湡锛岄槻姝㈤噸寤烘湡闂撮攣杩囨湡
            lockValue = acquireLockWithWait(lockKey, BREAKDOWN_LOCK_LEASE_SECONDS, LOCK_WAIT_MS);
            if (lockValue != null) {
                // 鑾峰彇閿佹垚鍔燂紝鍙岄噸妫€鏌ョ紦瀛橈紙singleflight锛氬彲鑳藉湪绛夊緟閿佹湡闂寸紦瀛樺凡琚叾浠栫嚎绋嬪～鍏咃級
                cached = stringOps.get(key);
                if (cached != null) {
                    log.debug("銆怰edisCacheGuard銆憇ingleflight 鍛戒腑宸插洖濉紦瀛橈紝澶嶇敤缁撴灉 | key={}", key);
                    if (clazz.isInstance(cached)) {
                        return clazz.cast(cached);
                    }
                    return stringOps.get(key, clazz);
                }

                // 鍥炴簮鏌ヨ
                T data = supplier.get();
                if (data != null) {
                    stringOps.set(key, data, expire);
                    log.info("銆怰edisCacheGuard銆戦槻鍑荤┛缂撳瓨鍥炲～ | key={} | ttl={}s", key, expire);
                }
                return data;
            } else {
                // 绛夊緟閿佽秴鏃讹紝鑷棆绛夊緟缂撳瓨灏辩华锛堟寚鏁伴€€閬匡級锛岄伩鍏嶅ぇ閲忕嚎绋嬪悓鏃跺洖婧愬嚮绌挎暟鎹簱
                return spinWaitForCache(key, supplier, clazz);
            }
        } finally {
            if (lockValue != null) {
                releaseLock(lockKey, lockValue);
            }
        }
    }

    /**
     * 闃茬紦瀛橀洩宕?- 闅忔満杩囨湡鏃堕棿
     *
     * <p>鍦ㄥ熀纭€ TTL 涓婂彔鍔犻殢鏈烘壈鍔紙0 ~ randomJitterMs锛夛紝
     * 浣夸笉鍚?key 鐨勮繃鏈熸椂闂村垎鏁ｅ紑锛岄伩鍏嶅ぇ閲忕紦瀛樺湪鍚屼竴鏃跺埢澶辨晥銆?/p>
     *
     * @param key            缂撳瓨 key
     * @param expire         鍩虹杩囨湡鏃堕棿锛堢锛?     * @param randomJitterMs 闅忔満鎵板姩鑼冨洿锛堟绉掞級锛屼細鍦?0~randomJitterMs 涔嬮棿闅忔満鍙犲姞
     * @param supplier       鍥炴簮鍔犺浇鏁版嵁鐨勯€昏緫
     * @param clazz          杩斿洖鍊肩被鍨?     * @param <T>            鏁版嵁绫诲瀷
     * @return 鏌ヨ缁撴灉
     */
    public <T> T antiAvalanche(String key, long expire, long randomJitterMs,
                                Supplier<T> supplier, Class<T> clazz) {
        // 鍏堝皾璇曚粠缂撳瓨鑾峰彇
        Object cached = stringOps.get(key);
        if (cached != null) {
            if (clazz.isInstance(cached)) {
                return clazz.cast(cached);
            }
            return stringOps.get(key, clazz);
        }

        // 缂撳瓨鏈懡涓紝鍥炴簮鏌ヨ
        T data = supplier.get();
        if (data != null) {
            long jitterSeconds = TimeUnit.MILLISECONDS.toSeconds(ThreadLocalRandom.current().nextLong(Math.max(1, randomJitterMs)));
            long expireWithJitter = expire + jitterSeconds;
            stringOps.set(key, data, expireWithJitter);
            log.debug("銆怰edisCacheGuard銆戦槻闆穿缂撳瓨鍐欏叆 | key={} | baseExpire={}s | jitter={}s | total={}s",
                    key, expire, jitterSeconds, expireWithJitter);
        }
        return data;
    }

    /**
     * 鑷棆绛夊緟缂撳瓨灏辩华锛堟寚鏁伴€€閬匡級锛岀敤浜庨槻绌块€忛攣鏈幏鍙栨椂绛夊緟鎸侀攣绾跨▼鍥炲～
     *
     * <p>涓?{@link #spinWaitForCache} 绫讳技锛屼絾棰濆澶勭悊绌哄€兼爣璁帮細
     * 褰撶紦瀛樹腑涓?{@link #NULL_PLACEHOLDER} 鏃惰〃绀烘暟鎹‘瀹炰笉瀛樺湪锛岀洿鎺ヨ繑鍥?null銆?/p>
     *
     * @param key        缂撳瓨 key
     * @param supplier   闄嶇骇鍥炴簮閫昏緫锛堣嚜鏃嬭秴鏃跺悗璋冪敤锛?     * @param clazz      杩斿洖鍊肩被鍨?     * @param nullCacheSec 绌哄€肩紦瀛樻椂闀匡紙绉掞級锛岄檷绾у洖婧愬悗鑻ョ粨鏋滀负 null 鍒欏啓鍏ョ┖鍊兼爣璁?     * @param <T>        鏁版嵁绫诲瀷
     * @return 鏌ヨ缁撴灉
     */
    private <T> T spinWaitForCacheOrPenetration(String key, Supplier<T> supplier,
                                                 Class<T> clazz, int nullCacheSec) {
        final long spinStart = System.currentTimeMillis();
        long backoff = SPIN_INITIAL_BACKOFF_MS;
        while (true) {
            Object cached = stringOps.get(key);
            if (cached != null) {
                if (NULL_PLACEHOLDER.equals(cached)) {
                    log.debug("銆怰edisCacheGuard銆戣嚜鏃嬬瓑寰呭懡涓┖鍊肩紦瀛?| key={}", key);
                    return null;
                }
                log.debug("銆怰edisCacheGuard銆戣嚜鏃嬬瓑寰呭懡涓紦瀛?| key={}", key);
                if (clazz.isInstance(cached)) {
                    return clazz.cast(cached);
                }
                return stringOps.get(key, clazz);
            }
            long elapsed = System.currentTimeMillis() - spinStart;
            if (elapsed >= SPIN_MAX_WAIT_MS) {
                log.warn("銆怰edisCacheGuard銆戦槻绌块€忚嚜鏃嬬瓑寰呰秴鏃讹紝闄嶇骇鐩存帴鍥炴簮 | key={}", key);
                T data = supplier.get();
                if (data == null) {
                    stringOps.set(key, NULL_PLACEHOLDER, nullCacheSec);
                }
                return data;
            }
            long sleepMs = Math.min(backoff, SPIN_MAX_WAIT_MS - elapsed);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("銆怰edisCacheGuard銆戦槻绌块€忚嚜鏃嬬瓑寰呰涓柇锛岄檷绾х洿鎺ュ洖婧?| key={}", key);
                return supplier.get();
            }
            backoff = Math.min(backoff * 2, SPIN_MAX_BACKOFF_MS);
        }
    }

    /**
     * 鑷棆绛夊緟缂撳瓨灏辩华锛堟寚鏁伴€€閬匡級锛岀敤浜庨槻鍑荤┛閿佺瓑寰呰秴鏃跺悗閬垮厤鐩存帴鍥炴簮
     *
     * <p>褰撹幏鍙栭攣瓒呮椂鏃朵笉绔嬪嵆璋冪敤 {@code supplier.get()}锛岃€屾槸浠ユ寚鏁伴€€閬挎柟寮忚嚜鏃嬫鏌ョ紦瀛橈紝
     * 绛夊緟鎸侀攣绾跨▼鍥炲～缂撳瓨銆備粎褰撹嚜鏃嬬瓑寰呬篃瓒呮椂鍚庢墠闄嶇骇鐩存帴鍥炴簮锛?     * 閬垮厤澶ч噺绛夊緟绾跨▼鍚屾椂鍑荤┛鏁版嵁搴撱€?/p>
     *
     * @param key      缂撳瓨 key
     * @param supplier 闄嶇骇鍥炴簮閫昏緫锛堣嚜鏃嬭秴鏃跺悗璋冪敤锛?     * @param clazz    杩斿洖鍊肩被鍨?     * @param <T>      鏁版嵁绫诲瀷
     * @return 鏌ヨ缁撴灉
     */
    private <T> T spinWaitForCache(String key, Supplier<T> supplier, Class<T> clazz) {
        final long spinStart = System.currentTimeMillis();
        long backoff = SPIN_INITIAL_BACKOFF_MS;
        while (true) {
            Object cached = stringOps.get(key);
            if (cached != null) {
                log.debug("銆怰edisCacheGuard銆戣嚜鏃嬬瓑寰呭懡涓紦瀛?| key={}", key);
                if (clazz.isInstance(cached)) {
                    return clazz.cast(cached);
                }
                return stringOps.get(key, clazz);
            }
            long elapsed = System.currentTimeMillis() - spinStart;
            if (elapsed >= SPIN_MAX_WAIT_MS) {
                log.warn("銆怰edisCacheGuard銆戣嚜鏃嬬瓑寰呰秴鏃讹紝闄嶇骇鐩存帴鍥炴簮 | key={}", key);
                return supplier.get();
            }
            long sleepMs = Math.min(backoff, SPIN_MAX_WAIT_MS - elapsed);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("銆怰edisCacheGuard銆戣嚜鏃嬬瓑寰呰涓柇锛岄檷绾х洿鎺ュ洖婧?| key={}", key);
                return supplier.get();
            }
            backoff = Math.min(backoff * 2, SPIN_MAX_BACKOFF_MS);
        }
    }

    /**
     * 鑾峰彇鍒嗗竷寮忛攣锛堥潪闃诲锛屽揩閫熷け璐ワ級锛岃幏鍙栨垚鍔熷悗鍚姩 WatchDog 鑷姩缁湡
     *
     * @param lockKey   閿侀敭
     * @param leaseTime 閿佺绾︽椂闂达紙绉掞級
     * @return 閿佸€硷紙鑾峰彇鎴愬姛锛夋垨 null锛堣幏鍙栧け璐ワ級
     */
    private String acquireLock(String lockKey, int leaseTime) {
        try {
            String lockValue = UUID.randomUUID().toString().replace("-", "");
            // 浣跨敤 redisTemplate 鐩存帴鎿嶄綔锛屼笌 releaseLock/renewLock 淇濇寔 key 澶勭悊涓€鑷?            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, lockValue, Duration.ofSeconds(leaseTime));
            if (Boolean.TRUE.equals(locked)) {
                // 鍚姩 WatchDog 鑷姩缁湡锛岄槻姝笟鍔℃墽琛屾椂闂磋秴杩?leaseTime 瀵艰嚧閿佽嚜鍔ㄩ噴鏀?                startWatchDog(lockKey, lockValue, leaseTime * 1000L);
                return lockValue;
            }
            return null;
        } catch (Exception e) {
            log.warn("銆怰edisCacheGuard銆戣幏鍙栭槻鎶ら攣澶辫触 | key={} | error={}", lockKey, e.getMessage());
            return null;
        }
    }

    /**
     * 鑾峰彇鍒嗗竷寮忛攣锛堝甫绛夊緟閲嶈瘯锛夛紝瀹炵幇 singleflight 绛夊緟璇箟
     *
     * <p>浣跨敤鎸囨暟閫€閬跨瓥鐣ュ湪 waitMs 鍐呭弽澶嶅皾璇曡幏鍙栭攣锛岃幏鍙栨垚鍔熷悗 WatchDog 鑷姩缁湡銆?     * 璋冪敤鏂瑰湪鑾峰彇閿佹垚鍔熷悗搴旇繘琛岀紦瀛樺弻閲嶆鏌ワ紝澶嶇敤鍏朵粬绾跨▼宸插洖濉殑缁撴灉銆?/p>
     *
     * @param lockKey   閿侀敭
     * @param leaseTime 閿佺绾︽椂闂达紙绉掞級
     * @param waitMs    鏈€澶х瓑寰呮椂闂达紙姣锛?     * @return 閿佸€硷紙鑾峰彇鎴愬姛锛夋垨 null锛堢瓑寰呰秴鏃讹級
     */
    private String acquireLockWithWait(String lockKey, int leaseTime, long waitMs) {
        long startTime = System.currentTimeMillis();
        long backoff = LOCK_WAIT_INITIAL_BACKOFF_MS;
        while (true) {
            String lockValue = acquireLock(lockKey, leaseTime);
            if (lockValue != null) {
                return lockValue;
            }
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed >= waitMs) {
                return null;
            }
            long sleepMs = Math.min(backoff, waitMs - elapsed);
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            backoff = Math.min(backoff * 2, LOCK_WAIT_MAX_BACKOFF_MS);
        }
    }

    /**
     * 閲婃斁鍒嗗竷寮忛攣锛堥€氳繃 Lua 鑴氭湰鍘熷瓙姣旇緝骞跺垹闄わ級锛屽苟鍋滄 WatchDog 缁湡
     *
     * @param lockKey   閿侀敭
     * @param lockValue 閿佸€?     */
    private void releaseLock(String lockKey, String lockValue) {
        // 鍏堝仠姝?WatchDog 缁湡锛岄伩鍏嶇画鏈熶换鍔′笌閲婃斁鎿嶄綔绔炰簤
        stopWatchDog(lockKey);
        try {
            redisTemplate.execute((RedisCallback<Object>) connection -> {
                byte[] keyBytes = lockKey.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
                byte[] scriptBytes = RELEASE_LOCK_LUA.getBytes(StandardCharsets.UTF_8);
                String sha = connection.scriptingCommands().scriptLoad(scriptBytes);
                connection.scriptingCommands().evalSha(sha,
                        org.springframework.data.redis.connection.ReturnType.INTEGER,
                        1, keyBytes, valueBytes);
                return null;
            });
        } catch (Exception e) {
            log.warn("銆怰edisCacheGuard銆戦噴鏀鹃槻鎶ら攣澶辫触 | key={} | error={}", lockKey, e.getMessage());
        }
    }

    /**
     * 鍚姩 WatchDog 鑷姩缁湡浠诲姟
     *
     * <p>缁湡闂撮殧涓?leaseTime 鐨?1/3锛堜笌 ydsz-pmis-common-lock 鐨?LockWatchDog 涓€鑷达級锛?     * 褰撶画鏈熸鏁拌秴杩?{@link #MAX_RENEW_TIMES} 鏃惰嚜鍔ㄥ仠姝紝闃叉涓氬姟绾跨▼鍗℃瀵艰嚧閿佹案涓嶉噴鏀俱€?/p>
     *
     * @param lockKey     閿侀敭
     * @param lockValue   閿佸€硷紙鐢ㄤ簬鏍￠獙鎸佹湁鑰咃級
     * @param leaseTimeMs 閿佺绾︽椂闂达紙姣锛?     */
    private void startWatchDog(String lockKey, String lockValue, long leaseTimeMs) {
        if (leaseTimeMs <= 0) {
            return;
        }
        long renewInterval = leaseTimeMs / 3;
        if (renewInterval <= 0) {
            renewInterval = Math.max(leaseTimeMs / 2, 1000);
        }
        AtomicBoolean running = new AtomicBoolean(true);
        ScheduledFuture<?> future = watchDogScheduler.scheduleAtFixedRate(
                () -> renewLockWithCheck(lockKey, lockValue, leaseTimeMs),
                renewInterval, renewInterval, TimeUnit.MILLISECONDS
        );
        WatchTask task = new WatchTask(lockKey, lockValue, leaseTimeMs, running, future);
        activeWatchTasks.put(lockKey, task);
        log.debug("銆怰edisCacheGuard銆戝惎鍔?WatchDog 缁湡 | key={} | leaseTime={}ms | interval={}ms",
                lockKey, leaseTimeMs, renewInterval);
    }

    /**
     * 鍋滄 WatchDog 缁湡浠诲姟
     *
     * @param lockKey 閿侀敭
     */
    private void stopWatchDog(String lockKey) {
        WatchTask task = activeWatchTasks.remove(lockKey);
        if (task != null) {
            task.running.set(false);
            task.future.cancel(false);
            log.debug("銆怰edisCacheGuard銆戝仠姝?WatchDog 缁湡 | key={}", lockKey);
        }
    }

    /**
     * 缁湡閿侊紙甯︽鏁伴檺鍒舵鏌ワ級
     *
     * @param lockKey     閿侀敭
     * @param lockValue   閿佸€?     * @param leaseTimeMs 绉熺害鏃堕棿锛堟绉掞級
     */
    private void renewLockWithCheck(String lockKey, String lockValue, long leaseTimeMs) {
        WatchTask task = activeWatchTasks.get(lockKey);
        if (task == null || !task.running.get()) {
            return;
        }
        if (task.renewCount >= MAX_RENEW_TIMES) {
            log.warn("銆怰edisCacheGuard銆慦atchDog 缁湡娆℃暟瓒呴檺锛屽仠姝㈢画鏈?| key={} | renewCount={}",
                    lockKey, task.renewCount);
            task.running.set(false);
            task.future.cancel(false);
            activeWatchTasks.remove(lockKey);
            return;
        }
        try {
            Boolean renewed = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                byte[] keyBytes = lockKey.getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = lockValue.getBytes(StandardCharsets.UTF_8);
                byte[] leaseBytes = String.valueOf(leaseTimeMs).getBytes(StandardCharsets.UTF_8);
                byte[] scriptBytes = RENEW_LOCK_LUA.getBytes(StandardCharsets.UTF_8);
                String sha = connection.scriptingCommands().scriptLoad(scriptBytes);
                Long result = connection.scriptingCommands().evalSha(sha,
                        org.springframework.data.redis.connection.ReturnType.INTEGER,
                        1, keyBytes, valueBytes, leaseBytes);
                return Long.valueOf(1L).equals(result);
            });
            if (Boolean.TRUE.equals(renewed)) {
                task.renewCount++;
                log.debug("銆怰edisCacheGuard銆慦atchDog 缁湡鎴愬姛 | key={} | renewCount={}", lockKey, task.renewCount);
            } else {
                // 閿佸凡涓嶅睘浜庡綋鍓嶆寔鏈夎€咃紙鍙兘宸茶繃鏈熻浠栦汉鑾峰彇锛夛紝鍋滄缁湡
                log.warn("銆怰edisCacheGuard銆慦atchDog 缁湡澶辫触锛岄攣鍙兘宸插け鏁?| key={}", lockKey);
                task.running.set(false);
                task.future.cancel(false);
                activeWatchTasks.remove(lockKey);
            }
        } catch (Exception e) {
            log.warn("銆怰edisCacheGuard銆慦atchDog 缁湡寮傚父 | key={} | error={}", lockKey, e.getMessage());
        }
    }
}
