package com.njydsz.common.batch.enums;

/**
 * 退出状态
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum ExitStatus {

    /** 未知 */
    UNKNOWN("UNKNOWN"),
    /** 执行中 */
    EXECUTING("EXECUTING"),
    /** 完成 */
    COMPLETED("COMPLETED"),
    /** 失败 */
    FAILED("FAILED"),
    /** 已停止 */
    STOPPED("STOPPED"),
    /** 已跳过 */
    SKIPPED("SKIPPED"),
    /** 无操作 */
    NOOP("NOOP");

    private final String code;

    ExitStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == STOPPED;
    }
}
