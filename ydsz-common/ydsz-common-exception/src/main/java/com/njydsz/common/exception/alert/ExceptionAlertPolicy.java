package com.njydsz.common.exception.alert;

/**
 * 异常告警策略接口。
 *
 * <p>负责检测异常码的触发频率，当某个错误码在滑动窗口内超过阈值时发布
 * {@link ExceptionAlertEvent}。消费方可通过 Spring
 * {@link org.springframework.context.ApplicationEventPublisher} 或自定义渠道推送告警。
 *
 * <p><b>内置实现：</b>{@link SlidingWindowAlertPolicy} — 基于内存滑动窗口，
 * 默认每错误码 5 分钟内触发 ≥ 10 次即告警。
 *
 * <p><b>扩展方式：</b>业务方可实现本接口并声明为 {@code @Bean} 替换默认策略，
 * 例如对接企业微信机器人、PagerDuty 等。
 *
 * @author ydsz-team
 * @since 2.4.0
 * @see SlidingWindowAlertPolicy
 * @see ExceptionAlertEvent
 */
public interface ExceptionAlertPolicy {

    /**
     * 记录一次异常触发，由异常处理器在捕获异常后调用。
     *
     * <p>实现类应在内部维护滑动窗口计数，若跨越阈值则发布 {@link ExceptionAlertEvent}。
     *
     * @param errorCode 触发的异常码
     * @param message   异常消息（供告警详情使用，可为 null）
     */
    void record(String errorCode, String message);

    /**
     * 重置指定错误码的计数（如人工确认告警后清零）。
     *
     * @param errorCode 需要重置的错误码
     */
    void reset(String errorCode);

    /**
     * 获取当前所有处于告警状态（触发过阈值）的错误码及其计数。
     *
     * @return 错误码 → 当前窗口内触发次数
     */
    java.util.Map<String, Integer> activeAlerts();
}
