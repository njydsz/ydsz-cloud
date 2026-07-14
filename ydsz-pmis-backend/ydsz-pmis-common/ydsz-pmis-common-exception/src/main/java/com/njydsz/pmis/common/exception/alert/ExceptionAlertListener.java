package com.njydsz.pmis.common.exception.alert;

/**
 * 异常告警监听器接口
 *
 * <p>实现此接口以接收异常告警事件。可通过 Spring Bean 注册多个监听器，
 * 支持多渠道告警（钉钉、邮件、短信、Slack 等）。
 *
 * <p>注意：{@link #onAlert(ExceptionAlertEvent)} 在调用线程同步执行，
 * 如果告警发送操作耗时较长，建议在实现内部使用异步线程池。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 * @see ExceptionAlertPublisher
 * @see ExceptionAlertEvent
 */
public interface ExceptionAlertListener {

    /**
     * 接收异常告警事件
     *
     * @param event 告警事件
     */
    void onAlert(ExceptionAlertEvent event);
}
