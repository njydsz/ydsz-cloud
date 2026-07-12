package com.njydsz.pmis.common.file.storage;

import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.file.config.FileProperties.ConcurrencyControl;
import com.njydsz.pmis.common.file.exception.FileExceptionCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 涓婁紶骞跺彂淇濇姢鍣?
 *
 * <p>闃叉瀵瑰悓涓€鏂囦欢锛坥bjectKey锛夌殑骞跺彂涓婁紶锛岄伩鍏嶆暟鎹珵浜夊拰瑕嗙洊闂銆?
 * 鍩轰簬 Redis 瀹炵幇锛屾敮鎸佷袱绉嶇瓥鐣ワ細
 * <ul>
 *   <li>{@code REJECT} - 宸叉湁涓婁紶姝ｅ湪杩涜鏃讹紝鐩存帴鎷掔粷鏂颁笂浼狅紙榛樿锛?/li>
 *   <li>{@code WAIT} - 绛夊緟鏃т笂浼犲畬鎴愬悗锛屽啀鎵ц鏂颁笂浼?/li>
 * </ul>
 *
 * <p><b>浣跨敤鏂瑰紡锛?/b>
 * <pre>{@code
 * UploadConcurrencyGuard guard = ...;
 * String lockToken = guard.acquire(objectKey);
 * try {
 *     // 鎵ц涓婁紶
 * } finally {
 *     guard.release(objectKey, lockToken);
 * }
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
public class UploadConcurrencyGuard {

    /**
     * Redis 閿侀敭鍓嶇紑
     */
    private static final String LOCK_KEY_PREFIX = "remi:file:upload:lock:";

    /**
     * 閿佺殑杩囨湡鏃堕棿锛堢锛夛紝闃叉涓氬姟寮傚父瀵艰嚧閿佹棤娉曢噴鏀?
     */
    private static final long LOCK_EXPIRE_SECONDS = 300;

    /**
     * WAIT 绛栫暐涓嬫瘡娆＄瓑寰呯殑闂撮殧锛堟绉掞級
     */
    private static final long WAIT_INTERVAL_MILLIS = 100;

    /**
     * WAIT 绛栫暐涓嬫渶澶х瓑寰呮椂闂达紙绉掞級
     */
    private static final long MAX_WAIT_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;
    private final ConcurrencyControl config;

    /**
     * 鍒涘缓骞跺彂淇濇姢鍣?
     *
     * @param redisTemplate Redis 妯℃澘
     * @param config        骞跺彂鎺у埗閰嶇疆
     */
    public UploadConcurrencyGuard(StringRedisTemplate redisTemplate, ConcurrencyControl config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    /**
     * 鑾峰彇涓婁紶閿?
     *
     * @param objectKey 鏂囦欢瀵硅薄閿?
     * @return 閿佷护鐗岋紝鐢ㄤ簬閲婃斁閿佹椂鏍￠獙
     * @throws BusinessException 褰撻厤缃负 REJECT 绛栫暐涓斿凡鏈変笂浼犳鍦ㄨ繘琛屾椂
     */
    public String acquire(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalArgumentException("objectKey must not be null or empty");
        }

        String lockKey = LOCK_KEY_PREFIX + objectKey;

        // 灏濊瘯闈為樆濉炶幏鍙栭攣
        String lockValue = tryAcquireNonBlocking(lockKey);
        if (lockValue != null) {
            return lockValue;
        }

        // 閿佸凡琚寔鏈夛紝鏍规嵁绛栫暐澶勭悊
        return handleLockHeld(lockKey, lockValue);
    }

    /**
     * 閲婃斁涓婁紶閿?
     *
     * <p>浣跨敤 Lua 鑴氭湰淇濊瘉鍘熷瓙鎬э細浠呭綋閿佸€煎尮閰嶆椂鎵嶅垹闄ゃ€?
     *
     * @param objectKey  鏂囦欢瀵硅薄閿?
     * @param lockToken  鑾峰彇閿佹椂杩斿洖鐨勪护鐗?
     */
    public void release(String objectKey, String lockToken) {
        if (objectKey == null || lockToken == null) {
            return;
        }

        String lockKey = LOCK_KEY_PREFIX + objectKey;
        // Lua: if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Object result = redisTemplate.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(lockKey), lockToken);
        if (Long.valueOf(1).equals(result)) {
            log.debug("[UploadGuard] lock released, key={}", lockKey);
        }
    }

    /**
     * 闈為樆濉炲皾璇曡幏鍙栭攣锛圫ETNX锛?
     *
     * @param lockKey 閿侀敭
     * @return 鎴愬姛杩斿洖閿佷护鐗岋紝澶辫触杩斿洖 null
     */
    private String tryAcquireNonBlocking(String lockKey) {
        String lockValue = UUID.randomUUID().toString().replace("-", "");
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(LOCK_EXPIRE_SECONDS));
        if (Boolean.TRUE.equals(success)) {
            log.debug("[UploadGuard] lock acquired, key={}", lockKey);
            return lockValue;
        }
        return null;
    }

    /**
     * 澶勭悊閿佸凡琚寔鏈夌殑鎯呭喌
     *
     * @param lockKey 閿侀敭
     * @param existingValue 宸插瓨鍦ㄧ殑閿佸€硷紙鐢ㄤ簬鏃ュ織锛?
     * @return 鑾峰彇閿佸悗杩斿洖鏂颁护鐗?
     * @throws BusinessException 褰?REJECT 绛栫暐鏃剁洿鎺ユ嫆缁?
     */
    private String handleLockHeld(String lockKey, String existingValue) {
        switch (config.getStrategy()) {
            case REJECT:
                log.warn("[UploadGuard] concurrent upload rejected, key={}", lockKey);
                throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
            case WAIT:
                return waitForLock(lockKey);
            default:
                // 鏈煡绛栫暐锛岄粯璁ゆ嫆缁?
                log.warn("[UploadGuard] unknown strategy, rejecting concurrent upload, key={}", lockKey);
                throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
        }
    }

    /**
     * WAIT 绛栫暐锛氱瓑寰呴攣閲婃斁鍚庨噸鏂拌幏鍙?
     *
     * @param lockKey 閿侀敭
     * @return 鑾峰彇閿佸悗杩斿洖鏂颁护鐗?
     */
    private String waitForLock(String lockKey) {
        long elapsedMillis = 0;
        long maxWaitMillis = MAX_WAIT_SECONDS * 1000;

        log.info("[UploadGuard] waiting for lock release, key={}", lockKey);

        while (elapsedMillis < maxWaitMillis) {
            try {
                // 鐭殏浼戠湢鍚庨噸璇?
                Thread.sleep(WAIT_INTERVAL_MILLIS);
                elapsedMillis += WAIT_INTERVAL_MILLIS;

                String lockValue = tryAcquireNonBlocking(lockKey);
                if (lockValue != null) {
                    log.info("[UploadGuard] lock acquired after waiting, key={}, waited={}ms",
                            lockKey, elapsedMillis);
                    return lockValue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[UploadGuard] interrupted while waiting for lock, key={}", lockKey);
                throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
            }
        }

        log.warn("[UploadGuard] wait timeout for lock, key={}, timeout={}s", lockKey, MAX_WAIT_SECONDS);
        throw new BusinessException(FileExceptionCode.UPLOAD_CONCURRENT_CONFLICT);
    }
}
