package com.njydsz.common.batch.enums;

/**
 * 批处理状态
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum BatchStatus {

    /** 启动中 */
    STARTING,
    /** 运行中 */
    STARTED,
    /** 停止中 */
    STOPPING,
    /** 已停止 */
    STOPPED,
    /** 已完成（成功） */
    COMPLETED,
    /** 失败 */
    FAILED,
    /** 已中止（手动终止） */
    ABANDONED,
    /** 未知 */
    UNKNOWN;

    public boolean isRunning() {
        return this == STARTING || this == STARTED;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == STOPPED || this == ABANDONED;
    }
}
