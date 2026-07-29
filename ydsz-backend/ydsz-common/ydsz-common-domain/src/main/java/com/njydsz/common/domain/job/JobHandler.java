package com.njydsz.common.domain.job;

/**
 * 任务处理器接口（cronjob 模块调度框架的核心契约）。
 *
 * <p>所有自定义任务处理器实现本接口的 {@link #execute(String)} 方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobHandler {

    /**
     * 执行任务。
     *
     * @param paramsJson 任务参数 JSON 字符串（可空）
     * @return 执行结果（可空，用于日志记录和回写任务日志）
     * @throws Exception 执行异常
     */
    Object execute(String paramsJson) throws Exception;

    /**
     * 执行分片任务。
     *
     * @param paramsJson 任务参数 JSON 字符串（可空）
     * @param ctx        分片上下文
     * @return 执行结果（可空）
     * @throws Exception 执行异常
     */
    default Object execute(String paramsJson, ShardingContext ctx) throws Exception {
        return execute(paramsJson);
    }
}
