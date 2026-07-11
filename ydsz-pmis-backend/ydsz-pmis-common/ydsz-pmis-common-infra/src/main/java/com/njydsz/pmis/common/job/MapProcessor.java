package com.njydsz.pmis.common.job;

/**
 * Map 处理器接口（P0-4）。
 *
 * <p>业务侧实现此接口，在 root task 中调用 {@link MapContext#map(java.util.List)} 产生子任务，
 * 框架（{@code MapTaskExecutor}）自动执行所有子任务。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Component("dataSyncMapProcessor")
 * public class DataSyncMapProcessor implements MapProcessor {
 *     @Override
 *     public ProcessResult process(MapContext context) throws Exception {
 *         if (context.isRootTask()) {
 *             // root task: 拆分数据范围产生子任务
 *             List<MapTask> subTasks = splitDateRanges(context.getTaskParams());
 *             context.map(subTasks);
 *             return ProcessResult.success();
 *         }
 *         // 子任务: 处理单个数据范围
 *         String params = context.getTaskParams();
 *         int count = syncDataForRange(params);
 *         return ProcessResult.success("synced " + count);
 *     }
 * }
 * }</pre>
 *
 * <p>Bean 名称需与 {@code pmis_job.handler} 一致；{@code pmis_job.job_type} 需设为 {@code MAP}。
 *
 * <p>对标 PowerJob 的 MapProcessor，区别在于 PowerJob 通过 MRUtils 产生子任务，
 * 本框架通过 {@link MapContext} 提供更简洁的 API。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MapProcessor {

    /**
     * 处理单个任务（root 或子任务）。
     *
     * <p>框架对 root task 和每个子任务都调用本方法，业务侧通过 {@link MapContext#isRootTask()}
     * 区分处理逻辑：
     * <ul>
     *   <li>root task：拆分数据产生子任务，调用 {@link MapContext#map(java.util.List)}</li>
     *   <li>子任务：处理单个数据分片，返回 {@link ProcessResult}</li>
     * </ul>
     *
     * @param context 包含任务参数和 map() 方法
     * @return 处理结果
     * @throws Exception 执行失败时抛出，框架捕获后转为 FAILED 状态
     */
    ProcessResult process(MapContext context) throws Exception;
}
