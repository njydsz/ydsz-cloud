package com.njydsz.common.domain.dag;

/**
 * DAG 节点状态枚举
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum DagNodeStatus {

    /** 待执行 */
    PENDING,
    /** 执行中 */
    RUNNING,
    /** 成功 */
    SUCCESS,
    /** 失败 */
    FAILED,
    /** 跳过 */
    SKIPPED,
    /** 超时 */
    TIMEOUT
}
