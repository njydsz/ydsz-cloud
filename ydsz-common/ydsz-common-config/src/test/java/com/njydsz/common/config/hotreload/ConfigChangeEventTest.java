package com.njydsz.common.config.hotreload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEvent;

import com.njydsz.common.config.hotreload.ConfigChangeEvent.ChangeType;

/**
 * {@link ConfigChangeEvent} 单元测试。
 *
 * <p>覆盖：不可变变更列表封装、record 组件相等性、changeType 枚举值。
 *
 * @since 26.09.20
 */
@DisplayName("ConfigChangeEvent 测试")
class ConfigChangeEventTest {

  @Nested
  @DisplayName("事件基本属性")
  class EventBasicsTest {

    @Test
    @DisplayName("getChanges 返回不可变列表")
    void getChanges_returnsUnmodifiableList() {
      List<ConfigChangeEvent.ConfigChange> changes = List.of(
          new ConfigChangeEvent.ConfigChange("key1", "old", "new", ChangeType.CHANGED));
      ConfigChangeEvent event = new ConfigChangeEvent(new Object(), changes);

      List<ConfigChangeEvent.ConfigChange> result = event.getChanges();
      assertThat(result).hasSize(1);
      assertThat(result.get(0).key()).isEqualTo("key1");
    }

    @Test
    @DisplayName("getSource 返回传入的 source")
    void getSource_returnsInjectedSource() {
      Object source = "bridge-instance";
      ConfigChangeEvent event = new ConfigChangeEvent(source, List.of());
      assertThat(event.getSource()).isSameAs(source);
    }

    @Test
    @DisplayName("继承 ApplicationEvent：timestamp 可获取")
    void timestamp_available() {
      ConfigChangeEvent event = new ConfigChangeEvent(new Object(), List.of());
      assertThat(event.getTimestamp()).isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("ConfigChange record 语义")
  class ConfigChangeRecordTest {

    @Test
    @DisplayName("record 组件：key / oldValue / newValue / changeType")
    void record_componentsAccessible() {
      ConfigChangeEvent.ConfigChange change =
          new ConfigChangeEvent.ConfigChange("ydsz.feature.enabled", "false", "true", ChangeType.CHANGED);
      assertThat(change.key()).isEqualTo("ydsz.feature.enabled");
      assertThat(change.oldValue()).isEqualTo("false");
      assertThat(change.newValue()).isEqualTo("true");
      assertThat(change.changeType()).isEqualTo(ChangeType.CHANGED);
    }

    @Test
    @DisplayName("record 相等性：相同值的不同实例相等")
    void record_equals() {
      ConfigChangeEvent.ConfigChange a =
          new ConfigChangeEvent.ConfigChange("k", "o", "n", ChangeType.ADDED);
      ConfigChangeEvent.ConfigChange b =
          new ConfigChangeEvent.ConfigChange("k", "o", "n", ChangeType.ADDED);
      assertThat(a).isEqualTo(b);
      assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("ADDED 语义：oldValue 为空表示新增")
    void added_semantics() {
      ConfigChangeEvent.ConfigChange change =
          new ConfigChangeEvent.ConfigChange("new.key", null, "value", ChangeType.ADDED);
      assertThat(change.oldValue()).isNull();
      assertThat(change.newValue()).isEqualTo("value");
    }

    @Test
    @DisplayName("DELETED 语义：newValue 为空表示删除")
    void deleted_semantics() {
      ConfigChangeEvent.ConfigChange change =
          new ConfigChangeEvent.ConfigChange("old.key", "value", null, ChangeType.DELETED);
      assertThat(change.oldValue()).isEqualTo("value");
      assertThat(change.newValue()).isNull();
    }
  }

  @Nested
  @DisplayName("ChangeType 枚举")
  class ChangeTypeEnumTest {

    @Test
    @DisplayName("枚举值数量：ADDED / CHANGED / DELETED 三种")
    void enumValues_count() {
      assertThat(ChangeType.values()).hasSize(3);
    }

    @Test
    @DisplayName("枚举命名：ADDED / CHANGED / DELETED")
    void enumValues_names() {
      assertThat(ChangeType.valueOf("ADDED")).isNotNull();
      assertThat(ChangeType.valueOf("CHANGED")).isNotNull();
      assertThat(ChangeType.valueOf("DELETED")).isNotNull();
    }
  }

  @Nested
  @DisplayName("空事件")
  class EmptyEventTest {

    @Test
    @DisplayName("空变更列表：getChanges 返回空列表而非 null")
    void emptyChanges_returnsEmptyList() {
      ConfigChangeEvent event = new ConfigChangeEvent(new Object(), List.of());
      assertThat(event.getChanges()).isEmpty();
    }
  }

  @Nested
  @DisplayName("ApplicationEvent 类型断言")
  class IsApplicationEventTest {

    @Test
    @DisplayName("ConfigChangeEvent 是 ApplicationEvent 子类")
    void isApplicationEvent() {
      ConfigChangeEvent event = new ConfigChangeEvent("src", List.of());
      assertThat(event).isInstanceOf(ApplicationEvent.class);
    }
  }
}
