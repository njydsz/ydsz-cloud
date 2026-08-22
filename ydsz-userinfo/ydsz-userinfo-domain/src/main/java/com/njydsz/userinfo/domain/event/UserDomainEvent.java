package com.njydsz.userinfo.domain.event;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import lombok.Getter;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.util.id.IdGenerator;

/**
 * 用户域领域事件。
 *
 * <p>封装用户模块的用户/角色/组织变更事件，继承 {@link DomainEvent}。 事件类型使用 {@link UserDomainEventType} 枚举，替代硬编码字符串常量。
 *
 * <p><b>发布方式：</b>
 *
 * <pre>{@code
 * applicationEventPublisher.publishEvent(
 *     UserDomainEvent.of(UserDomainEventType.USER_CREATED, userId, "USER", Map.of("username", "admin")));
 * }</pre>
 *
 * <p><b>消费方式（推荐事务提交后）：</b>
 *
 * <pre>{@code
 * &#64;TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * public void onUserCreated(UserDomainEvent event) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class UserDomainEvent extends DomainEvent {

  private static final long serialVersionUID = 1L;

  /**
   * 构造用户域事件。
   *
   * @param eventType 事件类型枚举（{@link UserDomainEventType}）
   * @param aggregateId 聚合根 ID
   * @param aggregateType 聚合根类型（USER / RoleDO / DepartmentDO）
   * @param metadata 扩展元数据
   */
  public UserDomainEvent(
      UserDomainEventType eventType,
      String aggregateId,
      String aggregateType,
      Map<String, Object> metadata) {
    super(
        IdGenerator.nextIdStr(),
        LocalDateTime.now(),
        eventType.getCode(),
        aggregateId,
        aggregateType,
        metadata != null ? metadata : Collections.emptyMap());
  }

  /**
   * 便捷工厂：创建用户域聚合根事件（aggregateType = "USER"）。
   *
   * @param eventType 事件类型枚举
   * @param userId 用户 ID
   * @param metadata 扩展元数据
   * @return 用户域事件实例
   */
  public static UserDomainEvent of(UserDomainEventType eventType, String userId, Map<String, Object> metadata) {
    return new UserDomainEvent(eventType, userId, "USER", metadata);
  }

  /**
   * 获取用户 ID（即 aggregateId，语义别名）。
   *
   * @return 用户 ID
   */
  public String getUserId() {
    return getAggregateId();
  }

  /**
   * 获取事件类型枚举。
   *
   * @return 事件类型枚举，无法识别时返回 null
   */
  public UserDomainEventType getEventTypeEnum() {
    return UserDomainEventType.fromCode(getEventType());
  }
}
