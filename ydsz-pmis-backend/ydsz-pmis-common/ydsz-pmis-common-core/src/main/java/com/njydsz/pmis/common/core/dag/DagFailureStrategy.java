package com.njydsz.pmis.common.core.dag;

/**
 * DAG 失败传播策略枚举。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum DagFailureStrategy {
    ABORT,
    CONTINUE,
    RETRY,
    SKIP_SUBSEQUENT
}
