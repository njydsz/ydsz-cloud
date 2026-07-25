package com.njydsz.common.batch.listener;

import com.njydsz.common.batch.model.BatchExecutionContext;

/**
 * Job 监听器
 *
 * <p>在 Job 生命周期关键节点触发回调，用于日志、监控、告警。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobListener {

    /**
     * Job 启动前
     */
    default void beforeJob(BatchExecutionContext context) {
    }

    /**
     * Job 完成后（无论成功失败）
     */
    default void afterJob(BatchExecutionContext context) {
    }
}
