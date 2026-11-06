package com.njydsz.literule.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.literule.domain.event.RuleConfigRefreshEvent.ChangeType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleConfigRefreshEvent} 单元测试。
 *
 * <p>覆盖：静态工厂、事件元数据、getChangeType/getOperator/getRuleCode、from 还原。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("规则配置刷新事件")
class RuleConfigRefreshEventTest {

  @Nested
  @DisplayName("of 静态工厂")
  class OfFactory {

    @Test
    @DisplayName("构建事件并填充元数据")
    void buildsEvent() {
      RuleConfigRefreshEvent event = RuleConfigRefreshEvent.of("RULE_001", ChangeType.UPDATE, "user-1");

      assertThat(event.getEventType()).isEqualTo(RuleConfigRefreshEvent.EVENT_TYPE);
      assertThat(event.getAggregateType()).isEqualTo(RuleConfigRefreshEvent.AGGREGATE_TYPE);
      assertThat(event.getRuleCode()).isEqualTo("RULE_001");
      assertThat(event.getChangeType()).isEqualTo(ChangeType.UPDATE);
      assertThat(event.getOperator()).isEqualTo("user-1");
      assertThat(event.getSchemaVersion()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(ChangeType.class)
    @DisplayName("全部变更类型均可正确设置")
    void allChangeTypes(ChangeType changeType) {
      RuleConfigRefreshEvent event = RuleConfigRefreshEvent.of("RULE_001", changeType, "user-1");
      assertThat(event.getChangeType()).isEqualTo(changeType);
    }
  }

  @Nested
  @DisplayName("全量刷新")
  class FullReload {

    @Test
    @DisplayName("ruleCode 为 null 表示全量刷新")
    void nullRuleCodeMeansFullReload() {
      RuleConfigRefreshEvent event = RuleConfigRefreshEvent.of(null, ChangeType.FULL_RELOAD, "system");
      assertThat(event.getRuleCode()).isNull();
      assertThat(event.getChangeType()).isEqualTo(ChangeType.FULL_RELOAD);
    }
  }

  @Nested
  @DisplayName("from 还原")
  class FromRestore {

    @Test
    @DisplayName("从 DomainEvent 还原事件")
    void restoresFromDomainEvent() {
      RuleConfigRefreshEvent original = RuleConfigRefreshEvent.of("RULE_002", ChangeType.DELETE, "user-2");
      DomainEvent raw = original;

      RuleConfigRefreshEvent restored = RuleConfigRefreshEvent.from(raw);

      assertThat(restored.getRuleCode()).isEqualTo("RULE_002");
      assertThat(restored.getChangeType()).isEqualTo(ChangeType.DELETE);
      assertThat(restored.getOperator()).isEqualTo("user-2");
      assertThat(restored.getEventType()).isEqualTo(RuleConfigRefreshEvent.EVENT_TYPE);
    }
  }

  @Nested
  @DisplayName("常量")
  class Constants {

    @Test
    @DisplayName("事件类型与聚合根类型常量")
    void constants() {
      assertThat(RuleConfigRefreshEvent.EVENT_TYPE).isEqualTo("RuleConfigRefresh");
      assertThat(RuleConfigRefreshEvent.AGGREGATE_TYPE).isEqualTo("Rule");
    }
  }
}
