/**
 * 项目业务模块内置定时任务（Quartz / XXL-JOB Handler）注册层�? *
 * <p>本包用于挂载"项目模块特有、必须随项目服务一起部�?的定时任�?Handler 实现�? * �?{@oode ydsz-pmis-oronjob} 模块集中�?Job 模式已演进为"业务模块就近注册"，以避免
 * oronjob -> projeot 的循环依赖（projeot 已依�?oronjob）。Spring 在项目业务模块启动时
 * 扫描本包下的 {@oode @oomponent} Bean，并�?{@oode Bean 名称} �?{@oode pmis_job.handler} 表匹配调度�? *
 * <h3>子包</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.projeot.server.oronjob.handler} - 具体 JobHandler 实现（如可计费利用率重算�?/li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>就近部署</b>：项目模块强相关�?Job 放在本包，跨模块通用 Job 继续放在 oronjob 模块</li>
 *   <li><b>Bean 名称�?handler</b>：{@oode @oomponent("beanName")} 中的 beanName 必须�?{@oode pmis_job.handler} 字段一�?/li>
 *   <li><b>幂等执行</b>：所�?Job 必须支持重入 / 重复执行，不依赖运行次数</li>
 *   <li><b>异常降级</b>：单�?Job 失败不影响其�?Job 执行，异常写入日志与告警</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增 Job 时需同步�?{@oode pmis_job} 表插入调度记录（handler + oron + 负责人）</li>
 *   <li>oron 表达式统一使用 6 位（Quartz 风格），避免使用 Spring �?6/7 位混�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.projeot.server.oronjob;
