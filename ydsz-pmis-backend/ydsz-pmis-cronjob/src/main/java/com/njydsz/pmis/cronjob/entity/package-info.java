/**
 * 定时任务模块 - 持久化实体层。
 *
 * <p>任务运行相关的数据库实体：
 * <ul>
 *   <li>{@code JobInfoDO}    - 任务定义（与 XXL-Job 调度中心同步）</li>
 *   <li>{@code JobLogDO}     - 任务执行日志（开始 / 结束 / 异常 / 耗时）</li>
 *   <li>{@code JobStatDO}    - 任务统计（按日 / 按周聚合）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.cronjob.entity;
