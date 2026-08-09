package com.njydsz.common.exception.enums;

/**
 * 异常级别枚举
 *
 * <p>用于标识异常的严重程度，便于分类处理和监控告警。
 * 监控告警系统可根据此级别决定是否触发告警、告警等级、告警通道。
 *
 * <p><b>使用建议：</b>
 * <ul>
 *   <li>{@link #INFO} - 提示性日志</li>
 *   <li>{@link #WARN} - 潜在问题但不影响业务</li>
 *   <li>{@link #ERROR} - 一般业务错误，需要告警但不影响系统可用性</li>
 *   <li>{@link #FATAL} - 严重错误，必须立即人工介入</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ExceptionLevel {

    /**
     * 信息级
     * <p>提示性信息，不需要特别处理
     */
    INFO("I", "信息"),

    /**
     * 警告级
     * <p>潜在问题，但不影响业务继续执行
     */
    WARN("W", "警告"),

    /**
     * 错误级
     * <p>一般业务错误，需要处理但不会导致系统崩溃
     */
    ERROR("E", "错误"),

    /**
     * 严重级
     * <p>严重错误，可能影响部分系统功能
     */
    FATAL("F", "严重");

    /** 级别编码 */
    private final String code;
    /** 级别描述 */
    private final String description;

    /**
     * 构造异常级别枚举
     *
     * @param code        级别编码
     * @param description 级别描述
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
}
