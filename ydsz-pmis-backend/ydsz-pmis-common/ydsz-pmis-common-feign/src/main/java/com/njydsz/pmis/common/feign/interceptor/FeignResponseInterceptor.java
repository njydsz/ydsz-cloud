package com.njydsz.pmis.common.feign.interceptor;

import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Feign 鍝嶅簲鎷︽埅鍣? *
 * <p>缁熶竴澶勭悊 Feign 瀹㈡埛绔殑鍝嶅簲锛屾彁渚涗互涓嬭兘鍔涳細
 * <ul>
 *   <li>鍝嶅簲鏃ュ織璁板綍锛堢姸鎬佺爜銆佽€楁椂銆佹柟娉曚俊鎭級</li>
 *   <li>鍝嶅簲鎸囨爣閲囬泦锛堢敤浜?Micrometer 鐩戞帶锛?/li>
 *   <li>寮傚父鍝嶅簲缁熶竴澶勭悊</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Slf4j
public class FeignResponseInterceptor implements feign.ResponseInterceptor {

    private final FeignResponseMetrics metrics;
    private final boolean logEnabled;
    private final long slowCallThresholdMillis;

    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled) {
        this(metrics, logEnabled, 0);
    }

    public FeignResponseInterceptor(@Nullable FeignResponseMetrics metrics, boolean logEnabled, long slowCallThresholdMillis) {
        this.metrics = metrics;
        this.logEnabled = logEnabled;
        this.slowCallThresholdMillis = slowCallThresholdMillis;
    }

    @Override
    public Object intercept(feign.InvocationContext context, Chain chain) throws Exception {
        long startTime = System.currentTimeMillis();
        String serviceName = extractServiceName(context);
        String httpMethod = extractMethod(context);

        try {
            Object result = context.proceed();
            Response response = context.response();
            long duration = System.currentTimeMillis() - startTime;
            recordSuccess(serviceName, httpMethod, response, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Response response = context.response();
            recordFailure(serviceName, httpMethod, response, duration, e);
            throw e;
        }
    }

    /**
     * 璁板綍鎴愬姛鍝嶅簲
     */
    private void recordSuccess(String serviceName, String httpMethod, Response response, long duration) {
        if (logEnabled && response != null) {
            log.info("[Feign] 鍝嶅簲鎴愬姛 | service={} | method={} | status={} | duration={}ms",
                    serviceName, httpMethod, response.status(), duration);
        }

        // P2: 鎱㈣皟鐢ㄦ娴?鈥?瓒呰繃闃堝€兼椂杈撳嚭 WARN 鏃ュ織
        if (slowCallThresholdMillis > 0 && duration >= slowCallThresholdMillis) {
            log.warn("[Feign] 鎱㈣皟鐢ㄥ憡璀?| service={} | method={} | status={} | duration={}ms | threshold={}ms",
                    serviceName, httpMethod, response != null ? response.status() : "N/A",
                    duration, slowCallThresholdMillis);
            if (metrics != null) {
                metrics.recordSlowCall(serviceName, httpMethod, duration, slowCallThresholdMillis);
            }
        }

        if (metrics != null && response != null) {
            metrics.recordSuccess(
                    serviceName,
                    httpMethod,
                    response.status(),
                    duration
            );
        }
    }

    /**
     * 璁板綍澶辫触鍝嶅簲
     */
    private void recordFailure(String serviceName, String httpMethod, Response response, long duration, Exception e) {
        log.warn("[Feign] 鍝嶅簲澶辫触 | service={} | method={} | status={} | duration={}ms | error={}",
                serviceName, httpMethod,
                response != null ? response.status() : "N/A",
                duration,
                e.getMessage());

        // P2: 澶辫触鍦烘櫙涔熸娴嬫參璋冪敤
        if (slowCallThresholdMillis > 0 && duration >= slowCallThresholdMillis) {
            log.warn("[Feign] 鎱㈣皟鐢ㄥ憡璀?| service={} | method={} | duration={}ms | threshold={}ms | error={}",
                    serviceName, httpMethod, duration, slowCallThresholdMillis, e.getClass().getSimpleName());
            if (metrics != null) {
                metrics.recordSlowCall(serviceName, httpMethod, duration, slowCallThresholdMillis);
            }
        }

        if (metrics != null) {
            metrics.recordFailure(
                    serviceName,
                    httpMethod,
                    response != null ? response.status() : 0,
                    duration,
                    e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 浠?configKey 鎻愬彇鏈嶅姟鍚嶇О
     * configKey 鏍煎紡涓?"ServiceName#methodName(params)"
     */
    private String extractServiceName(feign.InvocationContext context) {
        try {
            String configKey = context.toString();
            int hashIdx = configKey.indexOf('#');
            if (hashIdx > 0) {
                return configKey.substring(0, hashIdx);
            }
            return configKey;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 鎻愬彇 HTTP 鏂规硶
     */
    private String extractMethod(feign.InvocationContext context) {
        try {
            String configKey = context.toString();
            int hashIdx = configKey.indexOf('#');
            if (hashIdx > 0) {
                int parenIdx = configKey.indexOf('(', hashIdx);
                if (parenIdx > hashIdx) {
                    return configKey.substring(hashIdx + 1, parenIdx);
                }
            }
            return "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * Feign 鍝嶅簲鎸囨爣鎺ュ彛
     *
     * <p>鐢ㄤ簬闆嗘垚 Micrometer 鎴栧叾浠栫洃鎺х郴缁?     */
    public interface FeignResponseMetrics {
        /**
         * 璁板綍鎴愬姛鍝嶅簲
         *
         * @param service  鏈嶅姟鍚嶇О
         * @param method   HTTP 鏂规硶
         * @param status   HTTP 鐘舵€佺爜
         * @param duration 鑰楁椂锛堟绉掞級
         */
        void recordSuccess(String service, String method, int status, long duration);

        /**
         * 璁板綍澶辫触鍝嶅簲
         *
         * @param service   鏈嶅姟鍚嶇О
         * @param method    HTTP 鏂规硶
         * @param status    HTTP 鐘舵€佺爜
         * @param duration  鑰楁椂锛堟绉掞級
         * @param errorType 閿欒绫诲瀷
         */
        void recordFailure(String service, String method, int status, long duration, String errorType);

        /**
         * 璁板綍鎱㈣皟鐢紙P2 鍙娴嬫€у寮猴級
         *
         * @param service    鏈嶅姟鍚嶇О
         * @param method     HTTP 鏂规硶
         * @param duration   鑰楁椂锛堟绉掞級
         * @param threshold  鎱㈣皟鐢ㄩ槇鍊硷紙姣锛?         */
        void recordSlowCall(String service, String method, long duration, long threshold);
    }
}
