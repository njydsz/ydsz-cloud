package com.njydsz.common.feign.config;

import java.util.Locale;
import java.util.Set;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;

/**
 * 支持 HTTP 方法过滤的 Feign 重试器。
 *
 * <p>在 {@link Retryer.Default} 的基础上增加 HTTP 方法白名单过滤，
 * 仅对配置的方法（如 GET）进行重试，避免对非幂等操作（如 POST/PUT/DELETE）
 * 产生重复提交副作用。
 *
 * <p><b>重试逻辑：</b>
 * <ol>
 *   <li>从 {@link RetryableException} 中提取 HTTP 请求方法</li>
 *   <li>若方法不在白名单中，直接抛出异常（不重试）</li>
 *   <li>若方法在白名单中，委托给 {@link Retryer.Default} 执行指数退避重试</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Retryer.Default
 * @see FeignProperties.Retry
 */
public class MethodAwareRetryer implements Retryer {

    /** 委托的默认重试器，提供指数退避逻辑 */
    private final Retryer.Default delegate;

    /** 初始重试延迟（毫秒） */
    private final long period;

    /** 最大重试延迟（毫秒） */
    private final long maxPeriod;

    /** 最大重试次数（含首次调用） */
    private final int maxAttempts;

    /** 允许重试的 HTTP 方法集合（大写） */
    private final Set<String> retryableMethods;

    /**
     * 使用指定参数构造方法感知重试器。
     *
     * @param period           初始重试延迟（毫秒）
     * @param maxPeriod        最大重试延迟（毫秒）
     * @param maxAttempts      最大重试次数（含首次调用）
     * @param retryableMethods 允许重试的 HTTP 方法集合
     */
    public MethodAwareRetryer(long period, long maxPeriod, int maxAttempts,
                              Set<String> retryableMethods) {
        this.period = period;
        this.maxPeriod = maxPeriod;
        this.maxAttempts = maxAttempts;
        this.delegate = new Retryer.Default(period, maxPeriod, maxAttempts);
        this.retryableMethods = retryableMethods;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        Request request = e.request();
        if (request != null && request.httpMethod() != null) {
            String method = request.httpMethod().name().toUpperCase(Locale.ROOT);
            if (!retryableMethods.contains(method)) {
                throw e;
            }
        }
        delegate.continueOrPropagate(e);
    }

    @Override
    public Retryer clone() {
        return new MethodAwareRetryer(period, maxPeriod, maxAttempts, retryableMethods);
    }
}
