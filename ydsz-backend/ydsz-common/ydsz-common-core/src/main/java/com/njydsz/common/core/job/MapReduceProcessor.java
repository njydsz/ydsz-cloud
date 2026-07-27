package com.njydsz.common.core.job;

import java.util.List;

/**
 * MapReduce 任务处理器接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MapReduceProcessor extends MapProcessor {

    /**
     * 汇总子任务结果。
     *
     * @param subContexts 子任务上下文列表
     * @param rootContext Root 任务上下文
     * @return 汇总结果
     */
    ProcessResult reduce(List<MapContext> subContexts, MapContext rootContext);
}
