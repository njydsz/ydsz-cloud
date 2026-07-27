package com.njydsz.common.core.job;

import java.util.List;

/**
 * MapReduce 任务处理器接口
 *
 * <p>继承自 {@link MapProcessor}，增加 Reduce 阶段汇总能力。
 * 适用于需要「拆分-并行处理-汇总」完整工作流的大数据量场景。
 *
 * <p><b>执行流程：</b>
 * <ol>
 *   <li><b>Map 阶段</b>：调用 {@link #process(MapContext)}，Root 任务可向 subTasks 注入子任务</li>
 *   <li><b>子任务派发</b>：框架逐个派发 Root 构造的子任务到不同节点</li>
 *   <li><b>Reduce 阶段</b>：所有子任务完成后，调用 {@link #reduce(List, MapContext)} 汇总</li>
 * </ol>
 *
 * <p><b>使用场景：</b>大数据量批处理、跨分片聚合、分布式 ETL、统计报表生成等。
 *
 * <p><b>实现示例：</b>
 * <pre>{@code
 * @Component
 * public class OrderStatisticsProcessor implements MapReduceProcessor {
 *     @Override
 *     public ProcessResult process(MapContext ctx) {
 *         // Root：按日期拆分 30 个子任务
 *         if (ctx.isRoot()) {
 *             for (int i = 0; i < 30; i++) {
 *                 ctx.addSubTask("statisticsSubTask", "{\"day\":" + i + "}");
 *             }
 *             return ProcessResult.success();
 *         }
 *         // 子任务：统计当天订单数
 *         OrderDayCount count = orderService.countByDay(parseDay(ctx.getTaskParams()));
 *         ctx.getResults().put("count", count);
 *         return ProcessResult.success();
 *     }
 *
 *     @Override
 *     public ProcessResult reduce(List<MapContext> subContexts, MapContext rootContext) {
 *         long total = subContexts.stream().mapToLong(c -> (long) c.getResults().get("count")).sum();
 *         return ProcessResult.success("{\"total\":" + total + "}");
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MapProcessor
 */
public interface MapReduceProcessor extends MapProcessor {

    /**
     * 汇总子任务结果
     *
     * <p>所有子任务完成后，框架回调本方法。实现方遍历 {@code subContexts} 读取各分片结果，
     * 汇总后通过 {@link ProcessResult#success(String)} / {@link ProcessResult#failed(String)} 返回。
     *
     * <p><b>约束：</b>本方法应避免长事务；建议纯内存聚合后批量落库。
     *
     * @param subContexts 子任务上下文列表，按完成顺序排列
     * @param rootContext Root 任务上下文，可读原始任务参数
     * @return 汇总结果，决定整个 MapReduce 任务的最终状态
     */
    ProcessResult reduce(List<MapContext> subContexts, MapContext rootContext);
}
