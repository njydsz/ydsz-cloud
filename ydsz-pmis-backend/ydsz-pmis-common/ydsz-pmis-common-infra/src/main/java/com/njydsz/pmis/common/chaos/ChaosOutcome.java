package com.njydsz.pmis.common.chaos;

/**
 * 混沌实验结果 (批次 20 P3-1)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次20)
 */
public enum ChaosOutcome {

    /** 实验未启用 / 未命中 */
    NOT_TRIGGERED,

    /** 注入成功 (延迟已 sleep / 异常已抛出 / 错误已返回) */
    INJECTED,

    /** 实验跳过 (概率未命中) */
    SKIPPED_PROBABILITY,

    /** 实验被 feature flag 拦截 */
    BLOCKED_BY_FLAG
}
