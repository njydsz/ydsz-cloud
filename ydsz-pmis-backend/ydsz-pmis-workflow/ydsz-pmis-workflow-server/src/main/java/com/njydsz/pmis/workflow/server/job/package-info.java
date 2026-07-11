/**
 * 工作流定时任务处理器。
 *
 * <p>本包实现 {@code com.njydsz.pmis.common.job.JobHandler} 通用任务接口，
 * 由 PMIS 统一任务调度器（基于 XXL-Job / 自研）按 Cron 触发；每个 JobHandler
 * 对应一个 Bean 名称，运维在 {@code pmis_job} 表中配置 handler / cron / 参数即可调度。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.server.job.FlowTimeoutJobHandler} - SLA 超时扫描任务，
 *   定期扫描超期待办任务并执行相应动作（催办 / 升级 / 自动通过 / 自动驳回）</li>
 *   <li>{@link com.njydsz.pmis.workflow.server.job.FlowHistoryArchiveJobHandler} - 流程历史数据归档任务，
 *   将已结束实例迁移至历史表（{@code pmis_flow_his_*}），控制在线表体量</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>每个 JobHandler 必须捕获单条记录处理异常，<strong>单条失败不影响其他记录</strong>。</li>
 *   <li>JobHandler 不直接操作 HTTP / Web，仅依赖 Service / Mapper。</li>
 *   <li>可配置参数走 {@code FlowHistoryProperties} 等 {@code @ConfigurationProperties}，临时覆盖
 *       走 {@code paramsJson}。</li>
 *   <li>批量操作必须分页（{@code batchSize}）+ 超时控制（{@code maxProcessMs}），避免长事务。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.server.job;
