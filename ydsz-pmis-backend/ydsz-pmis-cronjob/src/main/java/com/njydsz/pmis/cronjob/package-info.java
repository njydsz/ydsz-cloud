/**
 * PMIS 分布式定时任务模块（ydsz-pmis-cronjob）。
 *
 * <p>本模块对外暴露"分布式定时任务"的调度能力。基于 XXL-Job 2.4+ 实现，
 * 任务由 PMIS 后台统一注册到 XXL-Job 调度中心，支持分片、并行、失败重试、邮件告警等能力。
 *
 * <p>本模块不实现具体业务定时任务（业务定时任务在 {@code ydsz-pmis-project} 等业务模块内），
 * 仅提供任务注册、调度、监控的统一通道。
 *
 * <h3>包结构</h3>
 * <ul>
 *   <li>{@code controller} - 任务执行 / 重跑 / 终止等管理接口</li>
 *   <li>{@code service}    - 任务管理业务服务（含 {@code service\impl}）</li>
 *   <li>{@code handler}    - 通用任务处理器（业务方实现）</li>
 *   <li>{@code dto}        - 任务管理 DTO（查询 / 重跑参数）</li>
 *   <li>{@code entity}     - 任务持久化实体</li>
 *   <li>{@code mapper}     - MyBatis-Plus Mapper</li>
 *   <li>{@code config}     - XXL-Job 客户端配置（执行器注册）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>业务定时任务需实现 {@code com.njydsz.pmis.common.job.JobHandler} 接口</li>
 *   <li>任务执行时间超过 1 分钟必须分段（{@code isSharding}）</li>
 *   <li>任务抛异常时由 {@code JobRunRecorder} 记录并发出告警</li>
 *   <li>幂等任务建议在方法上加 {@code @Idempotent}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.cronjob;
