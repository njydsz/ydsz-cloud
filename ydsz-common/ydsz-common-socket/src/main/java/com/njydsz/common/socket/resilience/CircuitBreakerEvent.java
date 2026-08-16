package com.njydsz.common.socket.resilience;

import com.njydsz.common.socket.resilience.WebSocketCircuitBreaker.State;

/**
 * 熔断器状态变更事件。
 *
 * <p>在熔断器状态流转时发出（CLOSED → OPEN → HALF_OPEN → CLOSED/OPEN），
 * 供指标采集、审计日志等下游消费者订阅。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public record CircuitBreakerEvent(
        String name,
        State from,
        State to,
        long timestamp) {

    /**
     * 创建状态变更事件。
     *
     * @param name  熔断器名称
     * @param from  原状态
     * @param to    目标状态
     * @return 事件实例
     */
    public static CircuitBreakerEvent of(String name, State from, State to) {
        return new CircuitBreakerEvent(name, from, to, System.currentTimeMillis());
    }

    /**
     * 状态对应的数值（用于指标暴露）。
     * <ul>
     *   <li>CLOSED = 0</li>
     *   <li>OPEN = 1</li>
     *   <li>HALF_OPEN = 2</li>
     * </ul>
     *
     * @return 状态数值
     */
    public int toMetricValue() {
        return switch (to) {
            case CLOSED -> 0;
            case OPEN -> 1;
            case HALF_OPEN -> 2;
        };
    }
}
