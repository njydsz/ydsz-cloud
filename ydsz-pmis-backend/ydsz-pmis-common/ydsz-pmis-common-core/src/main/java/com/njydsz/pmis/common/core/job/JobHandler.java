package com.njydsz.pmis.common.core.job;

/**
 * 定时任务处理器接口（兼容旧 com.njydsz.pmis.common.job.JobHandler）。
 *
 * <p>定义定时任务的执行契约，由 cronjob 模块调度引擎调用。
 * 实现类需注册为 Spring Bean，Bean 名称即为任务 handler 标识。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobHandler {

    /**
     * 执行定时任务。
     *
     * @param paramsJson 任务参数（JSON 格式，可为空）
     * @return 执行结果摘要
     * @throws Exception 执行异常
     */
    Object execute(String paramsJson) throws Exception;

    /**
     * 执行定时任务（带分片上下文）。
     *
     * <p>默认实现忽略分片上下文，委托给 {@link #execute(String)}。
     * 支持分片的 handler 可覆写此方法。
     *
     * @param paramsJson 任务参数（JSON 格式，可为空）
     * @param ctx        分片上下文
     * @return 执行结果摘要
     * @throws Exception 执行异常
     */
    default Object execute(String paramsJson, ShardingContext ctx) throws Exception {
        return execute(paramsJson);
    }
}
