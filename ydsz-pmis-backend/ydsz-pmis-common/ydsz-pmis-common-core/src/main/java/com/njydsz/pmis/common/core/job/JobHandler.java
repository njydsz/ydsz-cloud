package com.njydsz.pmis.common.core.job;

/**
 * 定时任务处理器接口（兼容旧 com.njydsz.pmis.common.job.JobHandler）。
 *
 * <p>定义定时任务的执行契约，由 cronjob 模块调度引擎调用。
 * 实现类需注册为 Spring Bean，Bean 名称即为任务 handler 标识。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Component("myJobHandler")
 * public class MyJobHandler implements JobHandler {
 *     @Override
 *     public Object execute(String paramsJson) throws Exception {
 *         // 业务逻辑
 *         return "success";
 *     }
 * }
 * }</pre>
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
}
