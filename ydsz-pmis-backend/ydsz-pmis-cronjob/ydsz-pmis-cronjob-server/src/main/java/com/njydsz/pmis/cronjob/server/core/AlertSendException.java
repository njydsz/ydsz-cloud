package com.njydsz.pmis.cronjob.server.core;

import com.njydsz.pmis.common.exception.custom.InfrastructureException;

/**
 * 告警发送异常（P5 告警 + 监控）。
 *
 * <p>由 {@link AlertDispatcher} 实现类在发送失败时抛出，由 {@link AlertDispatcher}
 * 捕获并记录到 {@code pmis_job_alert_log.error_message}。
 *
 * <p>继承 {@link InfrastructureException}，纳入 common-exception 统一异常体系，
 * 支持统一错误码、ProblemDetail (RFC 7807)、i18n、异常监控等能力。
 * 原为 checked exception（extends Exception），现改为 unchecked（extends RuntimeException），
 * 调用方无需声明 throws，简化代码。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class AlertSendException extends InfrastructureException {

    private static final long serialVersionUID = 1L;

    public AlertSendException(String message) {
        super(message);
    }

    public AlertSendException(String message, Throwable cause) {
        super(message);
        this.initCause(cause);
    }
}
