package com.njydsz.pmis.scheduler.handler;

/**
 * 任务处理器接口
 *
 * <p>实现此接口的 Bean 即可被 ydsz-pmis-scheduler 动态调度。
 * Bean 名称需与 pmis_job.handler 一致。
 */
public interface JobHandler {

    /**
     * 执行任务
     *
     * @param paramsJson 参数 JSON 字符串，可由调用方自定义
     * @return 执行结果（可空）
     * @throws Exception 执行失败时抛出
     */
    Object execute(String paramsJson) throws Exception;
}
