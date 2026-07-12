package com.njydsz.pmis.common.safe.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认安全事件告警日志实现
 *
 * <p>通过 {@link SecurityAlertListener} SPI 注册，
 * 使用 WARN 级别日志记录安全事件。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class DefaultSecurityAlertLogger implements SecurityAlertListener {

    private static final Logger log = LoggerFactory.getLogger(DefaultSecurityAlertLogger.class);

    @Override
    public void onSecurityEvent(SecurityEvent event) {
        log.warn(
                "[SECURITY ALERT] type={}, severity={}, uri={}, ip={}, userAgent={}, payload={}, time={}",
                event.getEventType(),
                event.getSeverity(),
                event.getRequestUri(),
                event.getSourceIp(),
                event.getUserAgent(),
                event.getAttackPayload(),
                event.getTimestamp()
        );
    }
}
