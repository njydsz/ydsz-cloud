package com.njydsz.common.core.job;

/**
 * Map 任务处理器接口
 *
 * <p>轻量级分布式任务抽象，业务方通过实现本接口注册可被调度框架调用的任务。
 * 与 {@link MapReduceProcessor} 的区别：本接口处理「单 Map / 单分片」场景，
 * 不涉及分片汇总逻辑；适合简单 ETL、数据同步、批处理等任务。
 *
 * <p><b>执行模型：</b>
 * <ul>
 *   <li>调度框架按 {@code ydsz.job.cron} 或触发条件拉起任务</li>
 *   <li>每次执行前构造一个 {@link MapContext} 上下文（包含分片信息、TraceId、JobLogger 等）</li>
 *   <li>框架回调 {@link #process(MapContext)}，执行业务逻辑</li>
 *   <li>框架根据返回的 {@link ProcessResult} 决定是否重试、是否记录审计</li>
 * </ul>
 *
 * <p><b>实现约束：</b>
 * <ol>
 *   <li>实现类必须标注 {@code @Component} 或在 {@code META-INF/spring.factories} 注册</li>
 *   <li>{@link #process(MapContext)} 必须是无状态、可重入的（同一实例可能并发执行）</li>
 *   <li>异常应包装为 {@link ProcessResult#fail(String)}，避免吞错</li>
 *   <li>长任务应定期调用 {@link JobLoggerHolder#info(String)} 输出心跳</li>
 * </ol>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * @Component
 * public class OrderSyncProcessor implements MapProcessor {
 *     @Override
 *     public ProcessResult process(MapContext context) {
 *         try {
 *             int count = orderService.syncPendingOrders();
 *             return ProcessResult.success("同步订单 " + count + " 条");
 *         } catch (Exception e) {
 *             return ProcessResult.fail("同步失败: " + e.getMessage());
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MapContext
 * @see ProcessResult
 * @see MapReduceProcessor
 */
public interface MapProcessor {

    /**
     * 处理 Map 任务
     *
     * <p>由调度框架回调，业务方在此实现核心业务逻辑。
     * 框架保证：
     * <ul>
     *   <li>执行前会初始化 {@link MapContext}（含 TraceId / 分片信息 / JobLogger）</li>
     *   <li>执行后根据 {@link ProcessResult} 决定重试或归档</li>
     * </ul>
     *
     * <p>业务方应保证：
     * <ul>
     *   <li>方法快速返回（建议 P95 &lt; 30s，复杂任务建议分批）</li>
     *   <li>方法幂等（支持重试）</li>
     *   <li>异常透传或包装为 {@link ProcessResult#fail(String)}</li>
     * </ul>
     *
     * @param context 任务执行上下文，含分片参数、JobLogger、TraceId 等
     * @return 处理结果，包含是否成功、消息、统计数据等
     */
    ProcessResult process(MapContext context);
}
