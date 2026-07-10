/**
 * 工作流 Spring 调度任务。
 *
 * <p>基于 {@code @Scheduled} 注解的进程内调度，与 {@code com.njydsz.pmis.workflow.job}
 * （分布式任务）职责区分：
 * <ul>
 *   <li>{@code scheduler} - 单实例进程内调度，无需分布式协调，适合本地化扫描 / 缓存预热。</li>
 *   <li>{@code job} - 分布式任务调度（XXL-Job 等），支持多实例负载均衡 / 失败转移。</li>
 * </ul>
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.scheduler.FlowAutoUrgeScheduler} - 自动催办调度器，
 *   定期扫描 SLA 超时任务并发送催办通知</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>调度任务执行必须 try-catch 顶层异常，<strong>禁止向外抛出</strong>，否则 Spring 会取消后续调度。</li>
 *   <li>多实例部署下需用 {@code FOR UPDATE SKIP LOCKED} 或 Redis 分布式锁去重。</li>
 *   <li>调度间隔与单次批大小必须可配置（{@code @Scheduled(fixedDelayString="${...}")}），
 *       避免硬编码导致压测 / 故障期无法调整。</li>
 *   <li>本包任务为"补充型"调度，<strong>关键业务必须使用 {@code job} 包分布式任务</strong>。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.scheduler;
