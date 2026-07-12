package com.njydsz.pmis.common.core.job;

/**
 * MapReduce 处理器接口（Map + Reduce）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MapReduceProcessor extends MapProcessor {

    /**
     * Reduce 阶段处理逻辑。
     *
     * @param context Map 上下文
     * @param results 各分片的 Map 结果
     * @return Reduce 结果
     * @throws Exception 处理异常
     */
    ProcessResult reduce(MapContext context, java.util.List<ProcessResult> results) throws Exception;
}
