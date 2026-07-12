/**
 * 定时任务抽象层。
 *
 * <p>封装 XXL-Job 客户端，提供统一的任务执行接口与运行轨迹记录。
 * 业务侧实现 {@link com.njydsz.pmis.common.job.JobHandler} 接口即可被调度中心拉起执行。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.job.JobHandler}     - 任务执行 SPI（业务方实现）</li>
 *   <li>{@link com.njydsz.pmis.common.job.JobRunRecorder} - 任务运行轨迹记录（开始 / 结束 / 异常 / 耗时）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>任务执行时间超过 1 分钟必须分段（{@code isSharding}）</li>
 *   <li>任务抛异常时由 {@code JobRunRecorder} 记录并发出告警</li>
 *   <li>幂等任务建议在方法上加 {@code @Idempotent}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.job;
