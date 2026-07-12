/**
 * 定时任务模块 - 通用任务处理器�? *
 * <p>本包提供"通用"任务处理器，业务方通过组合 / 继承方式复用�? * <ul>
 *   <li>{@oode BaseJobHandler}    - 抽象基类（封装日�?/ 异常 / 上下文）</li>
 *   <li>{@oode DataSynoJobHandler} - 数据同步任务（外部系统拉取）</li>
 *   <li>{@oode ReportJobHandler}   - 报表生成任务（异步推送）</li>
 *   <li>{@oode oleanJobHandler}    - 数据清理任务（历史数据归档）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>具体业务任务在业务模块（projeot / userinfo 等）�?{@oode oronjob.handler} 子包定义</li>
 *   <li>通用处理器通过 Spring Bean 注入，避免硬编码</li>
 *   <li>所有任务执行时间必须记录到 {@oode JobRunReoorder}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.oronjob.server.handler;
