package com.njydsz.pmis.common.job;

/**
 * 任务处理器接口（公共）
 *
 * <p>实现此接口的 Bean 即可被 ydsz-pmis-cronjob 动态调度。
 * Bean 名称需与 pmis_job.handler 一致。
 *
 * <p>为避免 cronjob 与业务模块之间的循环依赖，接口统一声明在 common 模块。
 *
 * <h3>P3 阶段扩展：分片支持</h3>
 * <p>业务侧可选择重写 {@link #execute(String, ShardingContext)} 接收分片上下文；
 * 默认实现忽略 ctx 调用 {@link #execute(String)}，保持向后兼容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobHandler {

    /**
     * 执行任务（不分片模式）。
     *
     * @param paramsJson 参数 JSON 字符串，可由调用方自定义
     * @return 执行结果（可空）
     * @throws Exception 执行失败时抛出
     */
    Object execute(String paramsJson) throws Exception;

    /**
     * 执行任务（支持分片上下文，P3 阶段引入）。
     *
     * <p>默认实现忽略 ctx，调用 {@link #execute(String)}，保持向后兼容。
     * 业务侧需分片能力时重写本方法。
     *
     * @param paramsJson 参数 JSON 字符串
     * @param ctx        分片上下文；非分片任务时 ctx.shardTotal=1，可为 null（极端兼容场景）
     * @return 执行结果（可空）
     * @throws Exception 执行失败时抛出
     */
    default Object execute(String paramsJson, ShardingContext ctx) throws Exception {
        return execute(paramsJson);
    }
}

