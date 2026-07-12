package com.njydsz.pmis.common.redis.service.ops;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Redis 浜嬪姟鎿嶄綔缁勪欢
 *
 * <p>鎻愪緵 Redis 浜嬪姟鎿嶄綔鎺ュ彛锛屽寘鎷細
 * <ul>
 *   <li>浜嬪姟鎵ц锛坋xecuteInTransaction锛?/li>
 * </ul>
 *
 * <p>浣跨敤 RedisTemplate 鐨?{@link SessionCallback} 瀹炵幇浜嬪姟锛?
 * 鎵€鏈夊湪鍥炶皟涓殑鎿嶄綔灏嗕綔涓轰竴涓師瀛愪簨鍔℃墽琛屻€?
 *
 * <p><b>浣跨敤绀轰緥锛?/b>
 * <pre>{@code
 * redisTransactionOps.executeInTransaction(operations -> {
 *     operations.opsForValue().set("key1", "value1");
 *     operations.opsForValue().set("key2", "value2");
 *     operations.opsForHash().put("hash1", "field1", "value1");
 *     return true;
 * });
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Slf4j
@Component
public class RedisTransactionOps {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisTransactionOps(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "RedisTemplate 涓嶈兘涓?null");
    }

    /**
     * 鍦?Redis 浜嬪姟涓墽琛屾搷浣?
     *
     * <p>浣跨敤 MULTI/EXEC 鍖呰９鍥炶皟涓殑鎵€鏈夋搷浣滐紝淇濊瘉鍘熷瓙鎬с€?
     * 鍥炶皟涓€氳繃浼犲叆鐨?{@link RedisTemplate} 鎵ц鐨勬墍鏈夊懡浠ゅ皢鍦ㄤ竴涓簨鍔′腑鎵ц銆?
     *
     * <p><b>娉ㄦ剰锛?/b>浜嬪姟涓殑鍛戒护涓嶄細绔嬪嵆鎵ц锛岃€屾槸鍦?EXEC 鏃舵壒閲忔墽琛岋紝
     * 鍥犳浜嬪姟涓棤娉曡幏鍙栦腑闂寸粨鏋滐紙濡?GET 鐨勮繑鍥炲€硷級銆?
     *
     * @param callback 浜嬪姟鍥炶皟鍑芥暟锛屽弬鏁颁负 RedisTemplate 瀹炰緥
     * @param <T>      鍥炶皟杩斿洖鍊肩被鍨?
     * @return 浜嬪姟鎵ц缁撴灉鍒楄〃锛圗XEC 杩斿洖鍊硷級锛屽け璐ユ椂杩斿洖 null
     */
    public <T> List<Object> executeInTransaction(Function<RedisTemplate<String, Object>, T> callback) {
        if (callback == null) {
            log.warn("銆怰edis銆戜簨鍔℃墽琛屽け璐ワ細鍥炶皟鍑芥暟涓嶈兘涓虹┖");
            return null;
        }
        try {
            return redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(@NonNull RedisOperations operations) {
                    operations.multi();
                    try {
                        callback.apply(redisTemplate);
                    } catch (Exception e) {
                        operations.discard();
                        throw e;
                    }
                    return operations.exec();
                }
            });
        } catch (Exception e) {
            log.error("銆怰edis銆戜簨鍔℃墽琛屽け璐?| error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 鍦?Redis 浜嬪姟涓墽琛屾搷浣滐紙鏃犺繑鍥炲€肩増鏈級
     *
     * <p>浣跨敤 MULTI/EXEC 鍖呰９鍥炶皟涓殑鎵€鏈夋搷浣滐紝淇濊瘉鍘熷瓙鎬с€?
     * 閫傜敤浜庝笉闇€瑕佸鐞嗕簨鍔¤繑鍥炵粨鏋滅殑鍦烘櫙銆?
     *
     * @param callback 浜嬪姟鍥炶皟鍑芥暟锛屽弬鏁颁负 RedisTemplate 瀹炰緥
     * @return true-浜嬪姟鎵ц鎴愬姛锛宖alse-浜嬪姟鎵ц澶辫触
     */
    public boolean executeInTransaction(Runnable callback) {
        if (callback == null) {
            log.warn("銆怰edis銆戜簨鍔℃墽琛屽け璐ワ細鍥炶皟鍑芥暟涓嶈兘涓虹┖");
            return false;
        }
        try {
            List<Object> results = redisTemplate.execute(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public List<Object> execute(@NonNull RedisOperations operations) {
                    operations.multi();
                    try {
                        callback.run();
                    } catch (Exception e) {
                        operations.discard();
                        throw e;
                    }
                    return operations.exec();
                }
            });
            return results != null;
        } catch (Exception e) {
            log.error("銆怰edis銆戜簨鍔℃墽琛屽け璐?| error={}", e.getMessage());
            return false;
        }
    }
}
