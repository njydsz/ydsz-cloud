package com.njydsz.common.batch.listener;

import com.njydsz.common.batch.model.BatchExecutionContext;

/**
 * Step 监听器
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface StepListener {

    /**
     * Step 启动前
     */
    default void beforeStep(BatchExecutionContext context) {
    }

    /**
     * Step 完成后
     */
    default void afterStep(BatchExecutionContext context) {
    }

    /**
     * 错误回调
     */
    default void onError(BatchExecutionContext context, Throwable ex) {
    }
}
