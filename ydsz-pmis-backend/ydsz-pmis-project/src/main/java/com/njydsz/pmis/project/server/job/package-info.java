/**
 * 项目业务模块内部定时任务（Job）层。
 *
 * <p>本包与 {@code project.cronjob} 包定位不同：本包存放"项目模块内嵌、随项目部署"的
 * {@code @Scheduled} 注解型 Job（轻量级、与业务紧耦合）；而 {@code project.cronjob}
 * 存放的是需要被调度平台动态管理的 JobHandler（重量级、需 pmis_job 表配置）。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.server.job.AfterSalesScanJobHandler} - 售后巡检 Job（每日 03:00，扫描质保/工单 SLA）</li>
 *   <li>{@link com.njydsz.pmis.project.server.job.AlertDispatchRetryJobHandler} - 告警派发重试 Job</li>
 *   <li>{@link com.njydsz.pmis.project.server.job.DailyReconcileJobHandler} - 日对账 Job</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>注解驱动</b>：定时规则写在 {@code @Scheduled(cron=...)} 上而非配置中心</li>
 *   <li><b>幂等</b>：所有 Job 必须可重复执行，不依赖执行次数或时间</li>
 *   <li><b>可观测</b>：执行耗时 / 成功 / 失败次数埋点到 Micrometer</li>
 *   <li><b>集群互斥</b>：多节点部署时配合 ShedLock 或 Redis 分布式锁避免重复执行</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>cron 表达式使用 6 位（秒 分 时 日 月 周），避免使用特殊字符 {@code ?}</li>
 *   <li>Job 内禁止调用 Thread.sleep / 阻塞 IO</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.server.job;
