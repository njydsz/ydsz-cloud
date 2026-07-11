/**
 * 项目业务模块内置定时任务（Quartz / XXL-JOB Handler）注册层。
 *
 * <p>本包用于挂载"项目模块特有、必须随项目服务一起部署"的定时任务 Handler 实现。
 * 原 {@code ydsz-pmis-cronjob} 模块集中式 Job 模式已演进为"业务模块就近注册"，以避免
 * cronjob -> project 的循环依赖（project 已依赖 cronjob）。Spring 在项目业务模块启动时
 * 扫描本包下的 {@code @Component} Bean，并按 {@code Bean 名称} 与 {@code pmis_job.handler} 表匹配调度。
 *
 * <h3>子包</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.server.cronjob.handler} - 具体 JobHandler 实现（如可计费利用率重算）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>就近部署</b>：项目模块强相关的 Job 放在本包，跨模块通用 Job 继续放在 cronjob 模块</li>
 *   <li><b>Bean 名称即 handler</b>：{@code @Component("beanName")} 中的 beanName 必须与 {@code pmis_job.handler} 字段一致</li>
 *   <li><b>幂等执行</b>：所有 Job 必须支持重入 / 重复执行，不依赖运行次数</li>
 *   <li><b>异常降级</b>：单个 Job 失败不影响其他 Job 执行，异常写入日志与告警</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增 Job 时需同步在 {@code pmis_job} 表插入调度记录（handler + cron + 负责人）</li>
 *   <li>cron 表达式统一使用 6 位（Quartz 风格），避免使用 Spring 的 6/7 位混用</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.server.cronjob;
