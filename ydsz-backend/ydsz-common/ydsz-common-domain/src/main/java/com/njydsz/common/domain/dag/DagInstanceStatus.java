package com.njydsz.common.domain.dag;

/**
 * DAG 实例状态枚举
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DagInstanceStatus {

    /** 待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 成功 */
    SUCCESS,
    /** 失败 */
    FAILED,
    /** 部分成功 */
    PARTIAL_SUCCESS,
    /** 已取消 */
    CANCELLED,
    /** 超时 */
    TIMEOUT
}
