package com.njydsz.workflow.domain.event;

/**
 * 领域事件发布器接口（Domain 层契约）。
 *
 * <p>定义领域事件的发布抽象，隔离领域层与具体事件传输机制（Spring ApplicationEvent、消息队列等）。
 * 领域层通过此接口发布事件，不依赖 Spring 框架或具体 MQ 实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>依赖倒置：domain 层定义接口，infra/server 层提供实现
 *   <li>简单性：仅提供 {@code publish} 方法，不暴露底层传输细节
 *   <li>扩展性：未来可支持异步发布、事务内发布等高级特性
 * </ul>
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>领域事件发布器置于 {@code domain/event/} 包下，
 * 作为领域事件基础设施的抽象接口（符合 §34.2.1 表格：event/ 领域事件类）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowDomainEvent 领域事件基类
 */
public interface DomainEventPublisher {

  /**
   * 发布领域事件。
   *
   * <p>事件发布后，所有注册的 {@code FlowDomainEventListener} 将收到通知并执行相应处理逻辑。
   * 实现类可选择同步或异步方式发布，调用方不应假设发布时机。
   *
   * @param event 领域事件（不可为 null）
   * @throws IllegalArgumentException 当 event 为 null 时
   */
  void publish(FlowDomainEvent event);
}
