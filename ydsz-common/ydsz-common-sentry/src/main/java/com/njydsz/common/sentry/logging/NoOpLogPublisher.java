package com.njydsz.common.sentry.logging;

import java.util.List;

import com.njydsz.common.sentry.domain.LogEvent;
import com.njydsz.common.sentry.spi.LogPublisher;

/**
 * 无操作日志发布器（No-Op Log Publisher）
 *
 * <p>当未配置任何日志发布器时作为默认实现，所有发布操作均为空操作（不执行任何操作）。
 *
 * <p><b>设计目的</b>：
 * <ul>
 *   <li>避免在未配置日志发布器时隐式降级到 Loki（可能导致日志被意外发送）</li>
 *   <li>显式表达"当前无日志发布器"的状态，便于监控和告警</li>
 *       {@link #isAvailable()} 返回 {@code false}，调用方可据此判断日志是否正常输出</li>
 * </ul>
 *
 * <p><b>使用场景</b>：
 * <ul>
 *   <li>开发/测试环境不需要日志上报</li>
 *   <li>日志发布器全部不可用时的兜底</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.0.0
 */
public class NoOpLogPublisher implements LogPublisher {

    /** 单例实例 */
    public static final NoOpLogPublisher INSTANCE = new NoOpLogPublisher();

    private NoOpLogPublisher() {
        // 私有构造函数，使用单例
    }

    @Override
    public boolean publish(LogEvent event) {
        // 无操作
        return true;
    }

    @Override
    public boolean publishBatch(List<LogEvent> events) {
        // 无操作
        return true;
    }

    @Override
    public boolean isAvailable() {
        // 显式返回 false，表示日志发布器不可用
        return false;
    }

    @Override
    public String getName() {
        return "no-op";
    }

    @Override
    public String getScheme() {
        return "none";
    }
}
