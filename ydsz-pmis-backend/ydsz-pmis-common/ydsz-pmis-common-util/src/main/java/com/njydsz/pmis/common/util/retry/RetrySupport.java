package com.njydsz.pmis.common.util.retry;

import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * 缁熶竴閲嶈瘯宸ュ叿绫? *
 * <p>鎻愪緵閫氱敤鐨勯噸璇曟墽琛岄€昏緫锛屾敮鎸佸绉嶉€€閬跨瓥鐣ャ€? * 缁熶竴浜?ydsz-pmis-common-redis銆乺emi-comm-job銆乺emi-comm-queue 绛夋ā鍧楃殑閲嶈瘯閫昏緫銆? *
 * <p><b>浣跨敤绀轰緥锛?/b></p>
 * <pre>{@code
 * // 浣跨敤鎸囨暟閫€閬块噸璇? * RetrySupport.withExponentialBackoff(3, 1000, 30000)
 *     .retryOn(e -> e instanceof TimeoutException)
 *     .execute(() -> remoteService.call());
 *
 * // 浣跨敤鍥哄畾闂撮殧閲嶈瘯
 * RetrySupport.withFixedInterval(5, 2000)
 *     .retryOn(e -> e instanceof IOException)
 *     .execute(() -> fileService.upload());
 *
 * // 甯︽姈鍔ㄥ洜瀛愮殑閲嶈瘯锛堥伩鍏嶆儕缇ゆ晥搴旓級
 * RetrySupport.withExponentialBackoff(3, 1000, 30000)
 *     .withJitter()
 *     .execute(() -> batchProcess());
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class RetrySupport {

    private RetrySupport() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 鍒涘缓鎸囨暟閫€閬块噸璇曟瀯寤哄櫒
     *
     * @param maxAttempts    鏈€澶ч噸璇曟鏁帮紙涓嶅惈棣栨鎵ц锛?     * @param initialDelayMs 鍒濆寤惰繜锛堟绉掞級
     * @param maxDelayMs     鏈€澶у欢杩燂紙姣锛?     * @return 閲嶈瘯鏋勫缓鍣?     */
    public static RetryBuilder withExponentialBackoff(int maxAttempts, long initialDelayMs, long maxDelayMs) {
        return new RetryBuilder(RetryStrategy.EXPONENTIAL_BACKOFF, maxAttempts, initialDelayMs, maxDelayMs);
    }

    /**
     * 鍒涘缓鍥哄畾闂撮殧閲嶈瘯鏋勫缓鍣?     *
     * @param maxAttempts 鏈€澶ч噸璇曟鏁帮紙涓嶅惈棣栨鎵ц锛?     * @param intervalMs  鍥哄畾闂撮殧锛堟绉掞級
     * @return 閲嶈瘯鏋勫缓鍣?     */
    public static RetryBuilder withFixedInterval(int maxAttempts, long intervalMs) {
        return new RetryBuilder(RetryStrategy.FIXED_INTERVAL, maxAttempts, intervalMs, intervalMs);
    }

    /**
     * 閲嶈瘯绛栫暐鏋氫妇
     */
    public enum RetryStrategy {
        /** 鎸囨暟閫€閬匡細delay = initialDelay * 2^attempt */
        EXPONENTIAL_BACKOFF,
        /** 鍥哄畾闂撮殧 */
        FIXED_INTERVAL
    }

    /**
     * 閲嶈瘯鏋勫缓鍣?     */
    public static class RetryBuilder {
        private final RetryStrategy strategy;
        private final int maxAttempts;
        private final long initialDelayMs;
        private final long maxDelayMs;
        private Predicate<Throwable> retryPredicate;
        private boolean withJitter = false;

        private RetryBuilder(RetryStrategy strategy, int maxAttempts, long initialDelayMs, long maxDelayMs) {
            this.strategy = strategy;
            this.maxAttempts = maxAttempts;
            this.initialDelayMs = initialDelayMs;
            this.maxDelayMs = maxDelayMs;
        }

        /**
         * 璁剧疆閲嶈瘯鏉′欢
         *
         * @param predicate 鍒ゆ柇寮傚父鏄惁鍙噸璇曠殑璋撹瘝
         * @return 閲嶈瘯鏋勫缓鍣?         */
        public RetryBuilder retryOn(Predicate<Throwable> predicate) {
            this.retryPredicate = predicate;
            return this;
        }

        /**
         * 鍚敤鎶栧姩鍥犲瓙锛堥伩鍏嶆儕缇ゆ晥搴旓級
         *
         * <p>鎶栧姩鍥犲瓙鑼冨洿锛歔0.5, 1.0]锛屽疄闄呭欢杩?= delay * (0.5 + random * 0.5)
         *
         * @return 閲嶈瘯鏋勫缓鍣?         */
        public RetryBuilder withJitter() {
            this.withJitter = true;
            return this;
        }

        /**
         * 鎵ц鏃犺繑鍥炲€肩殑浠诲姟
         *
         * @param task 瑕佹墽琛岀殑浠诲姟
         * @throws Exception 濡傛灉閲嶈瘯娆℃暟鑰楀敖鎴栭亣鍒颁笉鍙噸璇曞紓甯?         */
        public void execute(Runnable task) throws Exception {
            execute(() -> {
                task.run();
                return null;
            });
        }

        /**
         * 鎵ц鏈夎繑鍥炲€肩殑浠诲姟
         *
         * @param task 瑕佹墽琛岀殑浠诲姟
         * @param <T>  杩斿洖鍊肩被鍨?         * @return 浠诲姟鎵ц缁撴灉
         * @throws Exception 濡傛灉閲嶈瘯娆℃暟鑰楀敖鎴栭亣鍒颁笉鍙噸璇曞紓甯?         */
        public <T> T execute(Callable<T> task) throws Exception {
            if (task == null) {
                throw new IllegalArgumentException("Task cannot be null");
            }

            int attempt = 0;
            Throwable lastException = null;

            while (attempt <= maxAttempts) {
                try {
                    return task.call();
                } catch (Throwable e) {
                    lastException = e;

                    // 妫€鏌ユ槸鍚﹀彲閲嶈瘯
                    if (retryPredicate != null && !retryPredicate.test(e)) {
                        throw e;
                    }

                    // 妫€鏌ユ槸鍚﹁繕鏈夐噸璇曟満浼?                    if (attempt >= maxAttempts) {
                        break;
                    }

                    // 璁＄畻寤惰繜
                    long delay = calculateDelay(attempt);

                    // 绛夊緟
                    sleep(delay);

                    attempt++;
                }
            }

            // 閲嶈瘯鑰楀敖锛屾姏鍑烘渶鍚庡紓甯?            if (lastException != null) {
                if (lastException instanceof Exception) {
                    throw (Exception) lastException;
                }
                throw new RuntimeException(lastException);
            }

            throw new RuntimeException("Retry exhausted without exception");
        }

        /**
         * 璁＄畻寤惰繜鏃堕棿
         */
        private long calculateDelay(int attempt) {
            long delay;

            if (strategy == RetryStrategy.EXPONENTIAL_BACKOFF) {
                delay = initialDelayMs * (1L << attempt);
                delay = Math.min(delay, maxDelayMs);
            } else {
                // FIXED_INTERVAL
                delay = initialDelayMs;
            }

            // 搴旂敤鎶栧姩鍥犲瓙
            if (withJitter) {
                double jitterFactor = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;
                delay = (long) (delay * jitterFactor);
            }

            return delay;
        }

        /**
         * 鐫＄湢鎸囧畾鏃堕棿
         */
        private void sleep(long millis) {
            try {
                TimeUnit.MILLISECONDS.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", e);
            }
        }
    }

    // ==================== 渚挎嵎鏂规硶 ====================

    /**
     * 璁＄畻鎸囨暟閫€閬垮欢杩熸椂闂?     *
     * <p>鍏紡锛歞elay = min(initialDelay * 2^(attempt-1), maxDelay)
     *
     * @param attempt          褰撳墠閲嶈瘯娆℃暟锛堜粠1寮€濮嬶級
     * @param initialDelayMs   鍒濆寤惰繜锛堟绉掞級
     * @param maxDelayMs       鏈€澶у欢杩燂紙姣锛?     * @return 寤惰繜鏃堕棿锛堟绉掞級
     */
    public static long calculateExponentialBackoff(int attempt, long initialDelayMs, long maxDelayMs) {
        long delay = initialDelayMs * (1L << (attempt - 1));
        return Math.min(delay, maxDelayMs);
    }

    /**
     * 璁＄畻甯︽姈鍔ㄥ洜瀛愮殑鎸囨暟閫€閬垮欢杩熸椂闂?     *
     * <p>鍏紡锛歞elay = min(initialDelay * 2^(attempt-1) * jitter, maxDelay)
     * <p>鎶栧姩鍥犲瓙鑼冨洿锛歔0.5, 1.0]锛岄伩鍏嶅涓换鍔″悓鏃堕噸璇曞鑷寸殑"鎯婄兢鏁堝簲"
     *
     * @param attempt          褰撳墠閲嶈瘯娆℃暟锛堜粠1寮€濮嬶級
     * @param initialDelayMs   鍒濆寤惰繜锛堟绉掞級
     * @param maxDelayMs       鏈€澶у欢杩燂紙姣锛?     * @return 寤惰繜鏃堕棿锛堟绉掞級
     */
    public static long calculateExponentialBackoffWithJitter(int attempt, long initialDelayMs, long maxDelayMs) {
        long delay = initialDelayMs * (1L << (attempt - 1));
        double jitterFactor = 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5;
        delay = (long) (delay * jitterFactor);
        return Math.min(delay, maxDelayMs);
    }

    /**
     * 璁＄畻鍥哄畾闂撮殧寤惰繜鏃堕棿
     *
     * @param intervalMs 鍥哄畾闂撮殧锛堟绉掞級
     * @return 寤惰繜鏃堕棿锛堟绉掞級
     */
    public static long calculateFixedInterval(long intervalMs) {
        return intervalMs;
    }
}
