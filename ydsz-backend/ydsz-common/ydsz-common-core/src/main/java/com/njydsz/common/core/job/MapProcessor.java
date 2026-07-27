package com.njydsz.common.core.job;

/**
 * Map 任务处理器接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MapProcessor {

    /**
     * 处理 Map 任务。
     *
     * @param context 执行上下文
     * @return 处理结果
     */
    ProcessResult process(MapContext context);
}
