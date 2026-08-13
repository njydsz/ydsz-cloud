package com.njydsz.common.exception.enums;

/**
 * 异常级别枚举
 *
 * <p>用于标识异常的严重程度，作为元数据传递给指标系统和事件系统，
 * * 监控告警系统可根据此级别决定是否触发告警、告警等级、告警通道。
 *
 * <p><b>下游消费者：</b>
 * <ul>
 *     <li>{@link com.njydsz.common.exception.metrics.ExceptionMetrics} — 按级别聚合指标（exception.count 的 level tag）</li>
 *     <li>{@link com.njydsz.common.exception.event.ExceptionHandledEvent} — 事件携带 levelName，订阅者可按级别过滤告警</li>
 * </ul>
 *
 * <p><b>使用建议：</b>
 * <ul>
 *   <li>{@link #INFO} — 提示性日志，无需告警</li>
 *   <li>{@link #WARN} — 潜在问题但不影响业务，可记录但不告警</li>
 *   <li>{@link #ERROR} — 一般业务错误，按策略告警（如频率阈值触发）</li>
 *   <li>{@link #FATAL} — 严重错误，事件订阅者应触发即时告警（钉钉/短信/电话）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ExceptionLevel {

    /**
     * 信息级
     * <p>提示性信息，不需要特别处理
     * @param "I" "I" 参数说明
     * @param WARN("W" WARN("W" 参数说明
     * @param ERROR("E" ERROR("E" 参数说明
     * @param FATAL("F" FATAL("F" 参数说明
     * @param "严重" "严重" 参数说明
     * @return 处理结果
     */
    INFO("I", "信息"),

    /**
     * 警告级
     * <p>潜在问题，但不影响业务继续执行
     * @param "W" "W" 参数说明
     * @param ERROR("E" ERROR("E" 参数说明
     * @param FATAL("F" FATAL("F" 参数说明
     * @param "严重" "严重" 参数说明
     * @return 处理结果
     */
    WARN("W", "警告"),

    /**
     * 错误级
     * <p>一般业务错误，需要处理但不会导致系统崩溃
     * @param "E" "E" 参数说明
     * @param FATAL("F" FATAL("F" 参数说明
     * @param "严重" "严重" 参数说明
     * @return 处理结果
     */
    ERROR("E", "错误"),

    /**
     * 严重级
     * <p>严重错误，可能影响部分系统功能，事件订阅者应触发即时告警
     * @param "F" "F" 参数说明
     * @param "严重" "严重" 参数说明
     * @return 处理结果
     */
    FATAL("F", "严重");

    /**
     * 级别编码
     */
    private final String code;
    /**
     * 级别描述
     */
    private final String description;

    /**
     * 构造异常级别枚举
     *
     * @param code        级别编码
     * @param description 级别描述
     * @return 处理结果
     */
    ExceptionLevel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    /**
     * 获取级别描述
     *
     * @return 级别描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否需要立即告警（FATAL 级别）。
     *
     * <p>事件订阅者可据此决定是否触发即时告警通道（短信/电话），
     * 而非仅记录日志或发送普通通知。
     *
     * @return true 表示需要立即告警
     */
    public boolean requiresImmediateAlert() {
        return this == FATAL;
    }
}
