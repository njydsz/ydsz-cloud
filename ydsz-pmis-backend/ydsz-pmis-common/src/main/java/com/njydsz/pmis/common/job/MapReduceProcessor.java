package com.njydsz.pmis.common.job;

import java.util.List;

/**
 * MapReduce 处理器接口（P0-4）。
 *
 * <p>继承 {@link MapProcessor}，增加 {@link #reduce(MapContext, List)} 方法用于汇总所有子任务结果。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component("reportAggregateProcessor")
 * public class ReportAggregateProcessor implements MapReduceProcessor {
 *     @Override
 *     public ProcessResult process(MapContext context) throws Exception {
 *         if (context.isRootTask()) {
 *             List<MapTask> subTasks = splitByDepartment(context.getTaskParams());
 *             context.map(subTasks);
 *             return ProcessResult.success();
 *         }
 *         int count = aggregateDepartment(context.getTaskParams());
 *         return ProcessResult.success(String.valueOf(count));
 *     }
 *
 *     @Override
 *     public ProcessResult reduce(MapContext context, List<ProcessResult> taskResults) throws Exception {
 *         int total = 0;
 *         for (ProcessResult r : taskResults) {
 *             if (r.isSuccess() && r.getResult() != null) {
 *                 total += Integer.parseInt(r.getResult());
 *             }
 *         }
 *         return ProcessResult.success("total=" + total);
 *     }
 * }
 * }</pre>
 *
 * <p>Bean 名称需与 {@code pmis_job.handler} 一致；{@code pmis_job.job_type} 需设为 {@code MAP_REDUCE}。
 *
 * <p>对标 PowerJob 的 MapReduceProcessor，区别在于本框架在所有子任务执行完成后同步调用 reduce，
 * 不支持异步 reduce。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MapReduceProcessor extends MapProcessor {

    /**
     * 汇总所有子任务结果。
     *
     * <p>框架在所有子任务执行完成后调用本方法，传入每个子任务的 {@link ProcessResult}。
     * 业务侧可在此进行聚合计算（如求和、合并报表等）。
     *
     * <p>reduce 的返回值作为整个 MapReduce 任务的最终结果，写入 root TaskDO 和 JobLogDO。
     *
     * @param context     执行上下文（root task 上下文）
     * @param taskResults 所有子任务的处理结果列表（顺序与子任务产生顺序一致）
     * @return 汇总结果
     * @throws Exception 执行失败时抛出，框架捕获后转为 FAILED 状态
     */
    ProcessResult reduce(MapContext context, List<ProcessResult> taskResults) throws Exception;
}
