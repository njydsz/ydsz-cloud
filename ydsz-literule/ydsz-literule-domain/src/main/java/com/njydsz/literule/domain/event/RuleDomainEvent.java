package com.njydsz.literule.domain.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import lombok.Getter;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 规则域领域事件。
 *
 * <p>封装规则生命周期事件，继承 {@link DomainEvent}， 事件类型常量统一取自 {@link DomainEventTypes}（RULE_PUBLISHED /
 * RULE_DISABLED）。
 *
 * <p><b>发布方式：</b>
 *
 * <pre>{@code
 * applicationEventPublisher.publishEvent(
 *     RuleDomainEvent.of(DomainEventTypes.RULE_PUBLISHED, ruleId,
 *         Map.of("ruleCode", "loan-approval")));
 * }</pre>
 *
 * <p><b>消费方式（推荐事务提交后）：</b>
 *
 * <pre>{@code
 * &#64;TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onRulePublished(RuleDomainEvent event) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class RuleDomainEvent extends DomainEvent {

  private static final long serialVersionUID = 1L;

  /**
   * 构造规则域事件。
   *
   * @param eventType 事件类型（取自 {@link DomainEventTypes}）
   * @param ruleId 规则 ID（映射为 aggregateId）
   * @param aggregateType 聚合根类型（RULE）
   * @param metadata 扩展元数据
   */
  public RuleDomainEvent(
      String eventType, String ruleId, String aggregateType, Map<String, Object> metadata) {
    super(
        IdGenerator.nextIdStr(),
        LocalDateTime.now(),
        eventType,
        ruleId,
        aggregateType,
        metadata != null ? metadata : Collections.emptyMap());
  }

  /**
   * 便捷工厂：创建规则域事件。
   *
   * @param eventType 事件类型（取自 {@link DomainEventTypes}）
   * @param ruleId 规则 ID
   * @param metadata 扩展元数据
   * @return 规则域事件实例
   */
  public static RuleDomainEvent of(String eventType, String ruleId, Map<String, Object> metadata) {
    return new RuleDomainEvent(eventType, ruleId, "RULE", metadata);
  }

  /**
   * 获取规则 ID（即 aggregateId，语义别名）。
   *
   * @return 规则 ID
   */
  public String getRuleId() {
    return getAggregateId();
  }
}
