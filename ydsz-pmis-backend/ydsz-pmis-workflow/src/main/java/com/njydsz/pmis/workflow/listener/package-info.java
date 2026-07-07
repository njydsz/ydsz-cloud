/**
 * 工作流事件监听器。
 *
 * <p>监听 {@code FlowEventPublisher} 发布的流程事件（任务创建、任务完成、实例启动、
 * 实例结束等），用于在引擎主干之外完成解耦业务：通知落 outbox、业务回调（如项目立项后
 * 写入项目台账）、AI 统计、指标埋点等。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.listener.FlowNotifyOutboxListener} - 通知外发箱写入监听器，
 *   在事务内将事件写入 {@code pmis_flow_notify_outbox}，由 {@code NotifyOutboxScanner} 异步投递</li>
 *   <li>{@link com.njydsz.pmis.workflow.listener.ProjectInitiationFlowListener} - 项目立项流程监听器，
 *   实例完成 / 回滚时同步业务侧项目台账（如初始化项目编号、创建默认里程碑）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>监听器实现 {@code FlowEventListener} SPI，可由 Spring 容器统一管理并按 {@code @Order} 排序。</li>
 *   <li>监听器逻辑与主事务强一致性需求：必须 {@code Ordered.HIGHEST_PRECEDENCE} + 事务内写入，
 *       避免脏数据残留；非强一致需求建议走 outbox 异步。</li>
 *   <li>监听器异常默认不影响主流程（被事务切面吞掉），关键监听器需自行重试 / 死信。</li>
 *   <li>监听器禁止发送同步 HTTP 请求，跨服务调用必须走 MQ / 异步任务。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.listener;
