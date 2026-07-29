package com.njydsz.common.core.metrics;

/**
 * 核心模块指标采集器
 *
 * <p>统一管理 {@link CoreMetricsCallback} 的注册与调用，使用 {@code volatile} 静态 holder 模式
 * （与 {@link com.njydsz.common.core.response.BaseResponse} 的 MessageResolver 一致），
 * 保证在 Spring 容器就绪前即可安全调用。
 *
 * <p>当未注册回调时，所有方法空操作，零性能开销。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public final class CoreMetrics {

    private CoreMetrics() {
        throw new UnsupportedOperationException("Utility class");
    }

    /** 当前回调实例（volatile 保证多线程可见性） */
    private static volatile CoreMetricsCallback callback = CoreMetricsCallback.NOOP;

    /**
     * 注册指标回调
     *
     * @param callback 回调实现（null 则恢复为 NOOP）
     */
    public static void setCallback(CoreMetricsCallback callback) {
        CoreMetrics.callback = (callback != null) ? callback : CoreMetricsCallback.NOOP;
    }

    /**
     * 检查指标回调是否已注册
     *
     * @return 已注册返回 true
     */
    public static boolean isCallbackRegistered() {
        return callback != CoreMetricsCallback.NOOP;
    }

    /**
     * 记录 TraceId 生成事件
     *
     * @param strategy 生成策略名称
     */
    public static void recordTraceIdGenerated(String strategy) {
        callback.onTraceIdGenerated(strategy);
    }

    /**
     * 记录响应创建事件
     *
     * @param success 是否成功
     * @param code    响应码
     */
    public static void recordResponseCreated(boolean success, String code) {
        callback.onResponseCreated(success, code);
    }
}
