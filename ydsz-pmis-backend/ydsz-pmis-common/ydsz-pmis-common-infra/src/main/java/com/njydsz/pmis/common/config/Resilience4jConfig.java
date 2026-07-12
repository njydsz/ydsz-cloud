package com.njydsz.pmis.common.config;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Resilience4j 重试配置
 *
 * <p>大厂规范: 跨服务调用必须有有限重试，且必须满足以下条件:
 * <ul>
 *   <li>仅对网络抖动/超时重试，不对业务异常重试（避免雪崩）</li>
 *   <li>指数退避 + 随机抖动，避免重试风暴</li>
 *   <li>最大重试次数 ≤ 3 次</li>
 *   <li>仅对幂等接口（GET / 带幂等 token 的 POST）重试</li>
 * </ul>
 *
 * <p>本配置提供两个预置重试策略:
 * <ul>
 *   <li>feignRetry: Feign 调用重试，3 次指数退避 200ms→400ms→800ms</li>
 *   <li>dbRetry: 数据库操作重试，2 次退避 100ms→200ms（仅乐观锁冲突）</li>
 * </ul>
 *
 * <p>使用方式:
 * <pre>
 *   @Retry(name = "feignRetry")
 *   public Result&lt;UserDTO&gt; getUser(String id) { ... }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@Configuration
@ConditionalOnClass(Retry.class)
public class Resilience4jConfig {

    /** 最大重试次数（含首次调用） */
    private static final int MAX_ATTEMPTS = 3;
    /** 初始退避时长 */
    private static final Duration INITIAL_INTERVAL = Duration.ofMillis(200);
    /** 退避乘数 */
    private static final double MULTIPLIER = 2.0;
    /** 最大退避时长 */
    private static final Duration MAX_INTERVAL = Duration.ofSeconds(2);

    @PostConstruct
    public void initRetries() {
        RetryRegistry registry = RetryRegistry.ofDefaults();

        // Feign 调用重试：仅对超时/连接异常重试，不对业务异常重试
        RetryConfig feignConfig = RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .waitDuration(INITIAL_INTERVAL)
                .intervalFunction(attempt -> {
                    // 指数退避 + 10% 随机抖动（避免重试风暴同步）
                    long base = (long) (INITIAL_INTERVAL.toMillis() * Math.pow(MULTIPLIER, attempt - 1));
                    long jitter = (long) (base * 0.1 * Math.random());
                    return Math.min(base + jitter, MAX_INTERVAL.toMillis());
                })
                .retryOnException(shouldRetry())
                .build();
        registry.retry("feignRetry", feignConfig);

        // 数据库乐观锁冲突重试：仅对 OptimisticLockingFailureException 重试
        RetryConfig dbConfig = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(100))
                .retryOnException(ex -> ex.getClass().getName().contains("OptimisticLocking"))
                .build();
        registry.retry("dbRetry", dbConfig);

        log.info("[Resilience4j] 已注册重试策略: feignRetry (maxAttempts={}, 退避 {}ms×{}), dbRetry (maxAttempts=2)",
                MAX_ATTEMPTS, INITIAL_INTERVAL.toMillis(), MULTIPLIER);
    }

    /**
     * 重试异常判定: 仅对网络/超时异常重试
     * <p>不对以下异常重试:
     * <ul>
     *   <li>BizException: 业务异常（如参数错误、权限拒绝）</li>
     *   <li>NullPointerException / ClassCastException: 编程错误</li>
     *   <li>所有 RuntimeException 中包含 "biz" 标记的</li>
     * </ul>
     */
    private static Predicate<Throwable> shouldRetry() {
        return ex -> {
            // 超时异常: 重试
            if (ex instanceof TimeoutException) return true;
            // 连接异常: 重试
            String className = ex.getClass().getName();
            if (className.contains("ConnectException") ||
                className.contains("SocketTimeoutException") ||
                className.contains("ReadTimeoutException") ||
                className.contains("ServiceUnavailable")) {
                return true;
            }
            // Feign 异常: 仅对 503/504 重试
            if (className.contains("FeignException")) {
                String msg = ex.getMessage();
                return msg != null && (msg.contains("503") || msg.contains("504"));
            }
            // 业务异常: 不重试
            if (className.contains("BizException") ||
                className.contains("BizException") ||
                className.contains("IllegalArgumentException")) {
                return false;
            }
            // 其他 RuntimeException: 不重试（保守策略）
            return false;
        };
    }
}
