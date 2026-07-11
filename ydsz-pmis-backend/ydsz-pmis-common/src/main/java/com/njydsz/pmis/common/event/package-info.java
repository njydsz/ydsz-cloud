/**
 * 通用领域事件层 — 统一事件总线（P2-2 文档化增强）。
 *
 * <p>通过 Spring {@code ApplicationEventPublisher} 实现的进程内事件总线。
 * 业务侧发布事件，横切关注点（操作日志 / 缓存 / 通知 / 审计）通过 {@code @EventListener}
 * 或 {@code @TransactionalEventListener} 异步消费。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>事件命名采用过去时（如 {@code OperationLogEvent}、{@code ProjectChangeExecutedEvent}），
 *       表示"已发生的事情"</li>
 *   <li>事件对象不可变（{@code final} 字段 + 全参构造），保证监听器并发消费时数据一致</li>
 *   <li>事件发布通过事务事件监听器（{@code @TransactionalEventListener(phase = AFTER_COMMIT)}），
 *       保证事务回滚时不发送事件</li>
 *   <li>跨服务事件统一走 RocketMQ（{@code PmisMessageTopics}），不进本包</li>
 * </ul>
 *
 * <h3>事件总线注册表</h3>
 *
 * <p>以下是全项目所有 Spring 事件（进程内）的完整清单：
 *
 * <table border="1">
 * <caption>Spring Application Event 注册表</caption>
 * <tr><th>事件类</th><th>发布方</th><th>消费方</th><th>说明</th></tr>
 *
 * <tr><td>{@code UnifiedAlertEvent}</td>
 *     <td>common.alert / project.engine</td>
 *     <td>common.alert.UnifiedAlertDispatcher</td>
 *     <td>统一告警事件，触发消息发送 + 实时广播</td></tr>
 *
 * <tr><td>{@code OperationLogEvent}</td>
 *     <td>common.aspect.OperationLogAspect</td>
 *     <td>system.service.OperationLogService</td>
 *     <td>操作日志事件，异步持久化到 sys_operation_log</td></tr>
 *
 * <tr><td>{@code DataExportEvent}</td>
 *     <td>common.aspect.DataExportAuditAspect</td>
 *     <td>system.service.DataExportAuditService</td>
 *     <td>数据导出审计事件，记录导出操作</td></tr>
 *
 * <tr><td>{@code ReAuthEvent}</td>
 *     <td>common.aspect.RequireReAuthAspect</td>
 *     <td>system.service.ReAuthService</td>
 *     <td>二次认证事件，敏感操作前触发</td></tr>
 *
 * <tr><td>{@code FlowTaskCompletedEvent}</td>
 *     <td>workflow.service.FlowTaskSupport</td>
 *     <td>workflow.service.FlowNotificationService</td>
 *     <td>工作流任务完成事件，触发通知推送</td></tr>
 *
 * <tr><td>{@code FlowInstanceCompletedEvent}</td>
 *     <td>workflow.service.FlowInstanceServiceImpl</td>
 *     <td>workflow.service / project.service</td>
 *     <td>工作流实例完成事件，触发后续业务逻辑</td></tr>
 *
 * <tr><td>{@code TaskCompletedEvent}</td>
 *     <td>cronjob.core.dispatch.DefaultTaskDispatcher</td>
 *     <td>cronjob.core.dag.DagExecutor</td>
 *     <td>定时任务完成事件，触发 DAG 后继任务</td></tr>
 *
 * <tr><td>{@code AlertEvent}</td>
 *     <td>cronjob.core.alert.AlertTrigger</td>
 *     <td>cronjob.core.alert.AlertDispatcher</td>
 *     <td>告警触发事件，进入冷却去重 + 多通道派发</td></tr>
 *
 * <tr><td>{@code MessageSentEvent}</td>
 *     <td>message.service.MessageLogServiceImpl</td>
 *     <td>message.service.OutboundWebhookService</td>
 *     <td>消息发送完成事件，触发 Webhook 回调</td></tr>
 *
 * <tr><td>{@code ProjectChangeEvent}</td>
 *     <td>project.service.ProjectChangeServiceImpl</td>
 *     <td>project.engine.BudgetGuard</td>
 *     <td>项目变更事件，触发预算守卫检查</td></tr>
 *
 * <tr><td>{@code RuleConfigChangeEvent}</td>
 *     <td>literule.config.RuleAdminService</td>
 *     <td>literule.distributed.RedisRuleConfigBroadcaster</td>
 *     <td>规则配置变更事件，触发集群广播 + 本地缓存刷新</td></tr>
 *
 * <tr><td>{@code UserLoginEvent}</td>
 *     <td>userinfo.service.AuthServiceImpl</td>
 *     <td>system.service.LoginLogService</td>
 *     <td>用户登录事件，记录登录日志 + 安全审计</td></tr>
 *
 * <tr><td>{@code UserAccountEvent}</td>
 *     <td>userinfo.service.UserAccountServiceImpl</td>
 *     <td>system.service / message.service</td>
 *     <td>用户账户事件（创建/禁用/删除），触发通知 + 清理</td></tr>
 *
 * </table>
 *
 * <h3>跨服务事件（RocketMQ）</h3>
 *
 * <p>跨微服务的异步事件通过 RocketMQ Topic 传递，不使用 Spring ApplicationEvent。
 * 详见 {@code com.njydsz.pmis.common.constant.PmisMessageTopics}：
 *
 * <table border="1">
 * <caption>RocketMQ Topic 注册表</caption>
 * <tr><th>Topic</th><th>生产者</th><th>消费者</th><th>说明</th></tr>
 * <tr><td>{@code pmis-message-send}</td><td>各业务模块</td><td>message</td><td>异步消息发送</td></tr>
 * <tr><td>{@code pmis-rule-config-sync}</td><td>literule</td><td>literule（全节点）</td><td>规则配置集群同步</td></tr>
 * <tr><td>{@code pmis-alert-event}</td><td>cronjob / project</td><td>message</td><td>跨服务告警事件</td></tr>
 * <tr><td>{@code pmis-task-dispatch}</td><td>cronjob</td><td>cronjob（执行器节点）</td><td>定时任务分发</td></tr>
 * <tr><td>{@code pmis-cache-evict}</td><td>各业务模块</td><td>全服务</td><td>缓存失效广播</td></tr>
 * </table>
 *
 * <h3>使用规范</h3>
 * <ol>
 *   <li>新增事件时在此注册表中登记（类名 + 发布方 + 消费方 + 说明）</li>
 *   <li>事件类放在 {@code common.event} 包或各模块的 {@code event} 子包</li>
 *   <li>事件命名采用过去时（已发生的事情）</li>
 *   <li>事件对象必须不可变（final 字段）</li>
 *   <li>监听器使用 {@code @Async + @TransactionalEventListener(phase = AFTER_COMMIT)} 异步消费</li>
 *   <li>跨服务事件走 RocketMQ，不使用 Spring ApplicationEvent</li>
 *   <li>事件发布失败不应影响业务主流程（try-catch 降级）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.event;
