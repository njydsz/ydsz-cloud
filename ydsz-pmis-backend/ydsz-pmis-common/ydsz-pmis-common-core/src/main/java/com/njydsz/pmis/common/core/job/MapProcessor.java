package com.njydsz.pmis.common.core.job;

/**
 * MapReduce Map 阶段处理器接口。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MapProcessor {

    /**
     * Map 阶段处理逻辑。
     *
     * @param context Map 上下文
     * @return 处理结果
     * @throws Exception 处理异常
     */
    ProcessResult process(MapContext context) throws Exception;
}
