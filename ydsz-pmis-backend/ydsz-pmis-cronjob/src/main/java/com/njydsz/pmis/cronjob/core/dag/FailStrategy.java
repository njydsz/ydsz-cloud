package com.njydsz.pmis.cronjob.core.dag;

/**
 * 失败传播策略枚举（P4-3 DAG 工作流）。
 *
 * <p>定义前置任务失败后，后继任务的处理方式：
 * <ul>
 *   <li>{@link #FAIL_FAST}：前置失败则不触发后继（默认，适用于关键链路）</li>
 *   <li>{@link #CONTINUE_ON_FAIL}：前置失败仍触发后继（适用于通知/清理类后继）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum FailStrategy {

    /** 前置失败则不触发后继（默认，适用于关键链路） */
    FAIL_FAST,

    /** 前置失败仍触发后继（适用于通知/清理类后继） */
    CONTINUE_ON_FAIL;

    /**
     * 解析策略字符串，大小写不敏感；无效值返回 {@link #FAIL_FAST}。
     *
     * @param value 策略字符串（可为 null）
     * @return 对应枚举值
     */
    public static FailStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return FAIL_FAST;
        }
        try {
            return FailStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FAIL_FAST;
        }
    }
}
