/**
 * 定时任务模块 - 通用任务处理器。
 *
 * <p>本包提供"通用"任务处理器，业务方通过组合 / 继承方式复用：
 * <ul>
 *   <li>{@code BaseJobHandler}    - 抽象基类（封装日志 / 异常 / 上下文）</li>
 *   <li>{@code DataSyncJobHandler} - 数据同步任务（外部系统拉取）</li>
 *   <li>{@code ReportJobHandler}   - 报表生成任务（异步推送）</li>
 *   <li>{@code CleanJobHandler}    - 数据清理任务（历史数据归档）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>具体业务任务在业务模块（project / userinfo 等）的 {@code cronjob.handler} 子包定义</li>
 *   <li>通用处理器通过 Spring Bean 注入，避免硬编码</li>
 *   <li>所有任务执行时间必须记录到 {@code JobRunRecorder}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.cronjob.handler;
