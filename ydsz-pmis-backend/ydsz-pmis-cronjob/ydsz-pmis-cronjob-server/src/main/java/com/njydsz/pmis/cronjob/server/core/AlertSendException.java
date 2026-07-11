package com.njydsz.pmis.cronjob.server.core.alert;

/**
 * 告警发送异常（P5 告警 + 监控）。
 *
 * <p>由 {@link AlertDispatcher} 实现类在发送失败时抛出，由 {@link AlertDispatcher}
 * 捕获并记录到 {@code pmis_job_alert_log.error_message}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AlertSendException extends Exception {

    private static final long serialVersionUID = 1L;

    public AlertSendException(String message) {
        super(message);
    }

    public AlertSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
