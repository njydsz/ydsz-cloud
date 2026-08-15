package com.njydsz.common.event.api;

import java.util.List;

/**
 * Schema 校验异常
 *
 * <p>当事件 payload 不符合已注册的 JSON Schema 时抛出（仅在启用严格模式时抛出；
 * 默认模式下仅记录 WARN 日志，不阻断写入流程）。
 *
 * <p>此异常继承 {@link IllegalArgumentException}，可被 Spring 的异常转换机制
 * 自动映射为 400 Bad Request。
 *
 * @author ydsz-team
 * @since 1.6.0
 * @see JsonSchemaValidator
 * @see SchemaValidationResult
 */
public class SchemaValidationException extends IllegalArgumentException {

    /** 事件类型 */
    private final String eventType;

    /** 校验错误信息列表 */
    private final List<String> errors;

    /**
     * 构造 Schema 校验异常
     *
     * @param eventType 事件类型
     * @param errors    校验错误信息列表
     */
    public SchemaValidationException(String eventType, List<String> errors) {
        super("Schema validation failed for eventType=" + eventType + ", errors=" + errors);
        this.eventType = eventType;
        this.errors = errors != null ? List.copyOf(errors) : List.of();
    }

    /**
     * 获取事件类型
     *
     * @return 事件类型
     */
    public String getEventType() {
        return eventType;
    }

    /**
     * 获取校验错误信息列表
     *
     * @return 错误列表（不可修改）
     */
    public List<String> getErrors() {
        return errors;
    }
}
