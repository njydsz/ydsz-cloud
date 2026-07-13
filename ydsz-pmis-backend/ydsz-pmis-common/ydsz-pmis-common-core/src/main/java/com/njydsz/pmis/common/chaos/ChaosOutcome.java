package com.njydsz.pmis.common.chaos;

/**
 * 混沌实验注入结果。
 *
 * <p>表示一次混沌实验注入的执行结果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum ChaosOutcome {

    /** 注入成功 */
    INJECTED,

    /** 跳过（实验未启用或未命中） */
    SKIPPED,

    /** 实验不存在 */
    NOT_FOUND
}
