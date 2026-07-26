package com.njydsz.common.core.job;

/**
 * 任务处理器接口（ cronjob 模块调度框架的核心契约）。
 *
 * <p>对标 XXL-Job 的 IJobHandler / PowerJob 的 BasicProcessor，
 * 所有自定义任务处理器实现本接口的 {@link #execute(String)} 方法。
 *
 * <p>调度器（{@code DefaultTaskDispatcher}）根据 {@code jobType} 路由到对应的 JobHandler Bean，
 * Bean 名称由各实现类通过 {@code @Component("xxxJobHandler")} 显式指定。
 *
 * <p><b>实现示例：</b>
 * <pre>{@code
 * @Component("flowTimeoutJobHandler")
 * public class FlowTimeoutJobHandler implements JobHandler {
 *     @Override
 *     public Object execute(String paramsJson) throws Exception {
 *         // 解析 paramsJson 并执行业务逻辑
 *         return "success";
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobHandler {

    /**
     * 执行任务。
     *
     * <p>调度器在任务触发时调用本方法，传入任务配置的参数 JSON 字符串。
     * 实现方负责解析参数并执行业务逻辑。
     *
     * @param paramsJson 任务参数 JSON 字符串（可空）
     * @return 执行结果（可空，用于日志记录和回写任务日志）
     * @throws Exception 执行异常（调度器捕获后标记任务失败）
     */
    Object execute(String paramsJson) throws Exception;
}
