/**
 * 通用领域事件层。
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.event;
