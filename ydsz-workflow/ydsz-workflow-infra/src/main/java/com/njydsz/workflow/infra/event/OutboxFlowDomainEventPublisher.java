package com.njydsz.workflow.infra.event;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.workflow.domain.event.DomainEventPublisher;
import com.njydsz.workflow.domain.event.FlowDomainEvent;
import com.njydsz.workflow.domain.exception.WorkflowException;
import com.njydsz.workflow.domain.exception.WorkflowExceptionCode;

/**
 * 工作流领域事件发布适配器 — 委托 ydsz-common-event Outbox 门面统一发布。
 *
 * <p>实现 {@code domain/event/DomainEventPublisher}（DDD 依赖倒置接口），
 * 将 {@link FlowDomainEvent} 转换为 common-event 的 {@link DomainEvent}，
 * 委托统一门面写入 Outbox。修复 P0：此前该接口全仓零实现却被
 * {@code MessageEventServiceImpl} 构造注入，容器启动必然抛出
 * {@code NoSuchBeanDefinitionException}。
 *
 * <p><b>同名消歧说明：</b>common-event 门面类与本模块 domain 接口同名
 * （{@code DomainEventPublisher}）。本类 import 的是本模块接口（implements
 * 目标），common 门面以全限定名 {@code com.njydsz.common.event.publish.
 * DomainEventPublisher} 引用，避免单类型 import 遮蔽。
 *
 * <p><b>收敛要求（ADR-009）：</b>工作流模块发布跨模块事件必须经本适配器 →
 * common-event 门面 → Outbox 通道，禁止在业务代码中直接注入
 * {@code OutboxService} 构建 {@code OutboxMessage}。
 *
 * <p><b>降级策略：</b>通过 {@code ObjectProvider} 注入门面，common-event
 * 自动装配未生效（如 {@code ydsz.event.outbox.enabled=false}）时安全降级为
 * DEBUG 日志，不影响主流程 —— 与门面自身降级语义一致。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
@Slf4j
@Component
public class OutboxFlowDomainEventPublisher implements DomainEventPublisher {

  /** 工作流领域事件固定聚合类型 */
  private static final String AGGREGATE_TYPE_WORKFLOW = "Workflow";

  /** common-event 统一门面（可选依赖，未装配时安全降级） */
  private final ObjectProvider<com.njydsz.common.event.publish.DomainEventPublisher>
      publisherProvider;

  /**
   * 构造领域事件发布适配器。
   *
   * @param publisherProvider common-event 统一门面提供者
   */
  public OutboxFlowDomainEventPublisher(
      ObjectProvider<com.njydsz.common.event.publish.DomainEventPublisher> publisherProvider) {
    this.publisherProvider = publisherProvider;
  }

  /**
   * 发布领域事件到 Outbox。
   *
   * <p>转换字段映射：eventType = 事件类 SimpleName；aggregateType 固定
   * {@code Workflow}；payload = 事件全量 JSON；occurredAt 与 source 类型
   * 置入 metadata。发布失败仅告警，不影响业务主流程（Outbox 轮询器兜底）。
   *
   * @param event 领域事件（不可为 null）
   * @throws IllegalArgumentException 当 event 为 null 时
   */
  @Override
  public void publish(FlowDomainEvent event) {
    if (event == null) {
      throw new WorkflowException(WorkflowExceptionCode.FLOW_PARSING_ERROR, "event must not be null");
    }
    com.njydsz.common.event.publish.DomainEventPublisher delegate =
        publisherProvider.getIfAvailable();
    if (delegate == null) {
      log.debug(
          "[OutboxFlowEventPublisher] common-event publisher not available, skip: type={}",
          event.getClass().getSimpleName());
      return;
    }
    try {
      delegate.publish(toCommonEvent(event));
    } catch (Exception e) {
      log.warn(
          "[OutboxFlowEventPublisher] publish failed: type={}, err={}",
          event.getClass().getSimpleName(),
          e.getMessage());
    }
  }

  /**
   * 将工作流领域事件转换为 common-event 领域事件。
   *
   * @param event 工作流领域事件
   * @return common-event 领域事件
   */
  private DomainEvent toCommonEvent(FlowDomainEvent event) {
    Map<String, Object> metadata = new HashMap<>(2);
    metadata.put(
        "occurredAt", event.getOccurredAt() == null ? null : event.getOccurredAt().toString());
    metadata.put(
        "sourceType",
        event.getSource() == null ? null : event.getSource().getClass().getSimpleName());
    return DomainEvent.builder()
        .eventType(event.getClass().getSimpleName())
        .aggregateType(AGGREGATE_TYPE_WORKFLOW)
        .metadata(metadata)
        .build();
  }
}
