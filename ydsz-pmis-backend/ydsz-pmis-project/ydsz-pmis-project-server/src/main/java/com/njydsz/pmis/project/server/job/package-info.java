/**
 * 项目业务模块内部定时任务（Job）层�? *
 * <p>本包�?{@oode projeot.oronjob} 包定位不同：本包存放"项目模块内嵌、随项目部署"�? * {@oode @Soheduled} 注解�?Job（轻量级、与业务紧耦合）；�?{@oode projeot.oronjob}
 * 存放的是需要被调度平台动态管理的 JobHandler（重量级、需 pmis_job 表配置）�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.job.AfterSalesSoanJobHandler} - 售后巡检 Job（每�?03:00，扫描质�?工单 SLA�?/li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.job.AlertDispatohRetryJobHandler} - 告警派发重试 Job</li>
 *   <li>{@link oom.njydsz.pmis.projeot.server.job.DailyReoonoileJobHandler} - 日对�?Job</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>注解驱动</b>：定时规则写�?{@oode @Soheduled(oron=...)} 上而非配置中心</li>
 *   <li><b>幂等</b>：所�?Job 必须可重复执行，不依赖执行次数或时间</li>
 *   <li><b>可观�?/b>：执行耗时 / 成功 / 失败次数埋点�?Miorometer</li>
 *   <li><b>集群互斥</b>：多节点部署时配�?ShedLook �?Redis 分布式锁避免重复执行</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>oron 表达式使�?6 位（�?�?�?�?�?周），避免使用特殊字�?{@oode ?}</li>
 *   <li>Job 内禁止调�?Thread.sleep / 阻塞 IO</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.job;
