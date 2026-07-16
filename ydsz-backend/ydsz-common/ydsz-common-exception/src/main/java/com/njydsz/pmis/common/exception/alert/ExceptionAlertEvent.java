package com.njydsz.common.exception.alert;

import com.njydsz.common.exception.enums.ExceptionCategory;
import com.njydsz.common.exception.enums.ExceptionLevel;

import lombok.Getter;
import lombok.ToString;

/**
 * 异常告警事件
 *
 * <p>当异常级别为 FATAL 或 ERROR 时，由 {@link ExceptionAlertPublisher} 发布此事件。
 * 包含异常的完整上下文信息，供告警监听器使用。
 *
 * @author ydsz-team
 * @since 1.4.0
 * @see ExceptionAlertPublisher
 * @see ExceptionAlertListener
 */
@Getter
@ToString
public class ExceptionAlertEvent {

    /** 异常错误码 */
    private final String code;

    /** i18n 消息键 */
    private final String key;

    /** 异常消息（已解析的 i18n 消息） */
    private final String message;

    /** 异常级别 */
    private final ExceptionLevel level;

    /** 异常分类 */
    private final ExceptionCategory category;

    /** HTTP 状态码 */
    private final int httpStatus;

    /** 告警时间戳（毫秒） */
    private final long timestamp;

    /** 追踪 ID */
    private final String traceId;

    public ExceptionAlertEvent(String code, String key, String message,
                                ExceptionLevel level, ExceptionCategory category,
                                int httpStatus, long timestamp, String traceId) {
        this.code = code;
        this.key = key;
        this.message = message;
        this.level = level;
        this.category = category;
        this.httpStatus = httpStatus;
        this.timestamp = timestamp;
        this.traceId = traceId;
    }
}
