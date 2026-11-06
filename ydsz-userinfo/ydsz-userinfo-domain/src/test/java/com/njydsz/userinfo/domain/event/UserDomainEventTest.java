package com.njydsz.userinfo.domain.event;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserDomainEvent} 单元测试。
 *
 * <p>覆盖：构造与静态工厂、字段映射、getEventTypeEnum 解析、metadata 空安全。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("用户领域事件")
class UserDomainEventTest {

  @Nested
  @DisplayName("of 静态工厂")
  class Factory {

    @Test
    @DisplayName("of 创建事件，aggregateType 固定为 USER")
    void factorySetsAggregateType() {
      UserDomainEvent event =
          UserDomainEvent.of(UserDomainEventType.USER_CREATED, "user-1", Map.of("k", "v"));
      assertThat(event.getAggregateType()).isEqualTo("USER");
      assertThat(event.getAggregateId()).isEqualTo("user-1");
      assertThat(event.getUserId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("of 传入 null metadata 时使用空 Map")
    void factoryNullMetadata() {
      UserDomainEvent event = UserDomainEvent.of(UserDomainEventType.USER_CREATED, "user-1", null);
      assertThat(event.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("of 传入 metadata 时保留原值")
    void factoryKeepsMetadata() {
      Map<String, Object> meta = Map.of("username", "admin");
      UserDomainEvent event = UserDomainEvent.of(UserDomainEventType.USER_CREATED, "user-1", meta);
      assertThat(event.getMetadata()).containsEntry("username", "admin");
    }
  }

  @Nested
  @DisplayName("字段映射")
  class Fields {

    @Test
    @DisplayName("eventType 来自枚举 code")
    void eventTypeFromEnum() {
      UserDomainEvent event =
          UserDomainEvent.of(UserDomainEventType.USER_LOGIN, "user-1", Collections.emptyMap());
      assertThat(event.getEventType()).isEqualTo("USER_LOGIN");
    }

    @Test
    @DisplayName("eventId 非空")
    void eventIdNotBlank() {
      UserDomainEvent event =
          UserDomainEvent.of(UserDomainEventType.USER_CREATED, "user-1", Collections.emptyMap());
      assertThat(event.getEventId()).isNotBlank();
    }

    @Test
    @DisplayName("occurredAt 非空")
    void occurredAtNotNull() {
      UserDomainEvent event =
          UserDomainEvent.of(UserDomainEventType.USER_CREATED, "user-1", Collections.emptyMap());
      assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("schemaVersion 为 1")
    void schemaVersionIsOne() {
      UserDomainEvent event =
          UserDomainEvent.of(UserDomainEventType.USER_CREATED, "user-1", Collections.emptyMap());
      assertThat(event.getSchemaVersion()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("getEventTypeEnum 解析")
  class EventTypeEnum {

    @Test
    @DisplayName("可识别的 eventType 返回对应枚举")
    void knownType() {
      UserDomainEvent event =
          UserDomainEvent.of(UserDomainEventType.USER_CREATED, "user-1", Collections.emptyMap());
      assertThat(event.getEventTypeEnum()).isEqualTo(UserDomainEventType.USER_CREATED);
    }
  }
}
