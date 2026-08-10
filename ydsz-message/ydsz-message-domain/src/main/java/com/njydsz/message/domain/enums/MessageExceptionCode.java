package com.njydsz.message.domain.enums;

import com.njydsz.common.exception.registry.YdszExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCode;

import lombok.Getter;

/**
 * 消息中心模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，通过 {@link ExceptionCodeRegistry} 全局注册，
 * 支持 i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 * <ul>
 *   <li>B91001-B91099 模板</li>
 *   <li>B91101-B91199 通知/消息日志</li>
 *   <li>B91201-B91299 渠道/路由</li>
 *   <li>B91301-B91399 批量/灰度</li>
 *   <li>B91401-B91499 退订/偏好/反馈
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "message", description = "消息中心")
public enum MessageExceptionCode implements ExceptionCode {

    // ==================== B91001-B91099 模板 ====================
    TEMPLATE_NOT_FOUND("B91001", "message.template.not.found", 404),
    TEMPLATE_CODE_DUPLICATE("B91002", "message.template.code.duplicate"),
    TEMPLATE_AUDIT_PENDING("B91003", "message.template.audit.pending"),
    TEMPLATE_AUDIT_REJECTED("B91004", "message.template.audit.rejected"),
    TEMPLATE_VARIABLE_MISSING("B91005", "message.template.variable.missing"),

    // ==================== B91101-B91199 通知/消息日志 ====================
    NOTIFICATION_NOT_FOUND("B91101", "message.notification.not.found", 404),
    MESSAGE_LOG_NOT_FOUND("B91102", "message.log.not.found", 404),
    MESSAGE_SEND_FAILED("B91103", "message.send.failed", 500),
    MESSAGE_RECALL_FAILED("B91104", "message.recall.failed"),

    // ==================== B91201-B91299 渠道/路由 ====================
    CHANNEL_NOT_CONFIGURED("B91201", "message.channel.not.configured"),
    CHANNEL_SEND_FAILED("B91202", "message.channel.send.failed", 500),
    ROUTE_RULE_NOT_FOUND("B91203", "message.route.rule.not.found", 404),
    CHANNEL_BLOCKED("B91204", "message.channel.blocked"),

    // ==================== B91301-B91399 批量/灰度 ====================
    BATCH_NOT_FOUND("B91301", "message.batch.not.found", 404),
    BATCH_ALREADY_RUNNING("B91302", "message.batch.already.running"),
    CANARY_NOT_FOUND("B91303", "message.canary.not.found", 404),

    // ==================== B91401-B91499 退订/偏好/反馈 ====================
    UNSUBSCRIBE_TOKEN_INVALID("B91401", "message.unsubscribe.token.invalid"),
    PREFERENCE_NOT_FOUND("B91402", "message.preference.not.found", 404),
    FEEDBACK_NOT_FOUND("B91403", "message.feedback.not.found", 404);

    /** 错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    MessageExceptionCode(String code, String key) {
        this(code, key, 400);
    }

    MessageExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }
}
