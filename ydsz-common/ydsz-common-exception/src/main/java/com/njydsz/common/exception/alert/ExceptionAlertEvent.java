package com.njydsz.common.exception.alert;
import java.time.LocalDateTime;

/**
 * 异常告警事件。
 *
 * <p>当某个异常码在滑动窗口内触发次数超过阈值时，由 {@link ExceptionAlertPolicy} 发布。
 * 消费方可订阅此事件执行自定义告警逻辑（钉钉/飞书/邮件/短信等）。
 *
 * @param errorCode  触发的异常码
 * @param count      滑动窗口内累计触发次数
 * @param threshold  触发告警的阈值
 * @param windowSize 滑动窗口大小（分钟）
 * @param firstSeen  窗口内首次触发时间
 * @param lastSeen   窗口内末次触发时间
 * @param sampleMsg  最近一次异常消息截取（前 200 字符）
 *
 * @author ydsz-team
 * @since 2.4.0
 */
public record ExceptionAlertEvent(
        String errorCode,
        int count,
        int threshold,
        int windowSize,
        LocalDateTime firstSeen,
        LocalDateTime lastSeen,
        String sampleMsg
) {
    @Override
    public String toString() {
        return String.format("ExceptionAlertEvent{code='%s', count=%d, threshold=%d, window=%dmin, first=%s, last=%s}",
                errorCode, count, threshold, windowSize, firstSeen, lastSeen);
    }
}
