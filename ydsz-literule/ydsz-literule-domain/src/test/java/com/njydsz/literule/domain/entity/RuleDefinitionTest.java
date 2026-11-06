package com.njydsz.literule.domain.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.literule.domain.enums.RuleStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RuleDefinition} 领域行为单元测试。
 *
 * <p>覆盖：状态流转（publish/disable/revertToDraft）、可编辑判定、生效窗口、启用/灰度判定。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("规则定义实体")
class RuleDefinitionTest {

  private RuleDefinition definition() {
    RuleDefinition d = new RuleDefinition();
    d.setRuleCode("RULE_001");
    d.setStatus(RuleStatus.DRAFT.name());
    d.setIsEnabled(true);
    return d;
  }

  @Nested
  @DisplayName("状态枚举")
  class StatusEnum {

    @Test
    @DisplayName("getStatusEnum 解析已设置状态")
    void resolvesStatus() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.PUBLISHED.name());
      assertThat(d.getStatusEnum()).isEqualTo(RuleStatus.PUBLISHED);
    }

    @Test
    @DisplayName("getStatusEnum 未设置返回 null")
    void nullWhenUnset() {
      RuleDefinition d = new RuleDefinition();
      assertThat(d.getStatusEnum()).isNull();
    }

    @Test
    @DisplayName("setStatusEnum 设置合法枚举")
    void setsStatus() {
      RuleDefinition d = new RuleDefinition();
      d.setStatusEnum(RuleStatus.DISABLED);
      assertThat(d.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("setStatusEnum null 抛异常")
    void nullThrows() {
      RuleDefinition d = new RuleDefinition();
      assertThatThrownBy(() -> d.setStatusEnum(null))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  @DisplayName("publish 发布")
  class Publish {

    @Test
    @DisplayName("DRAFT → PUBLISHED 成功并记录审核信息")
    void draftToPublished() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.DRAFT.name());
      d.publish("reviewer-1");
      assertThat(d.getStatus()).isEqualTo("PUBLISHED");
      assertThat(d.getReviewedBy()).isEqualTo("reviewer-1");
      assertThat(d.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("null 状态按 DRAFT 处理可发布")
    void nullStatusTreatedAsDraft() {
      RuleDefinition d = new RuleDefinition();
      d.publish("r");
      assertThat(d.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("DISABLED → PUBLISHED 可直接恢复发布")
    void disabledToPublished() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.DISABLED.name());
      d.publish("r");
      assertThat(d.getStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("PUBLISHED → 重复发布（同状态）不抛异常")
    void republishSameStateOk() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.PUBLISHED.name());
      d.publish("r");
      assertThat(d.getStatus()).isEqualTo("PUBLISHED");
    }
  }

  @Nested
  @DisplayName("disable 停用")
  class Disable {

    @Test
    @DisplayName("PUBLISHED → DISABLED 成功")
    void publishedToDisabled() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.PUBLISHED.name());
      d.disable();
      assertThat(d.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("DRAFT → DISABLED 成功")
    void draftToDisabled() {
      RuleDefinition d = definition();
      d.disable();
      assertThat(d.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("null 状态抛异常")
    void nullThrows() {
      RuleDefinition d = new RuleDefinition();
      assertThatThrownBy(d::disable).isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  @DisplayName("revertToDraft 回退草稿")
  class RevertToDraft {

    @Test
    @DisplayName("PUBLISHED → DRAFT 清空审核信息")
    void publishedToDraftClearsReview() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.PUBLISHED.name());
      d.setReviewedBy("r");
      d.setReviewedAt(LocalDateTime.now());
      d.setReviewComment("ok");
      d.revertToDraft();
      assertThat(d.getStatus()).isEqualTo("DRAFT");
      assertThat(d.getReviewedBy()).isNull();
      assertThat(d.getReviewedAt()).isNull();
      assertThat(d.getReviewComment()).isNull();
    }

    @Test
    @DisplayName("DISABLED → DRAFT 成功")
    void disabledToDraft() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.DISABLED.name());
      d.revertToDraft();
      assertThat(d.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("DRAFT → DRAFT（同状态）不抛异常")
    void draftToDraftOk() {
      RuleDefinition d = definition();
      d.revertToDraft();
      assertThat(d.getStatus()).isEqualTo("DRAFT");
    }
  }

  @Nested
  @DisplayName("isEditable 可编辑判定")
  class Editable {

    @Test
    @DisplayName("DRAFT 可编辑")
    void draftEditable() {
      assertThat(definition().isEditable()).isTrue();
    }

    @Test
    @DisplayName("DISABLED 可编辑")
    void disabledEditable() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.DISABLED.name());
      assertThat(d.isEditable()).isTrue();
    }

    @Test
    @DisplayName("PUBLISHED 不可编辑")
    void publishedNotEditable() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.PUBLISHED.name());
      assertThat(d.isEditable()).isFalse();
    }
  }

  @Nested
  @DisplayName("isInEffectiveWindow 生效窗口")
  class EffectiveWindow {

    @Test
    @DisplayName("now 为 null 返回 true")
    void nullNowReturnsTrue() {
      assertThat(definition().isInEffectiveWindow(null)).isTrue();
    }

    @Test
    @DisplayName("无边界限制返回 true")
    void noBoundsReturnsTrue() {
      assertThat(definition().isInEffectiveWindow(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("早于 effectiveFrom 返回 false")
    void beforeFromReturnsFalse() {
      RuleDefinition d = definition();
      d.setEffectiveFrom(LocalDateTime.now().plusDays(1));
      assertThat(d.isInEffectiveWindow(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("晚于 effectiveTo 返回 false")
    void afterToReturnsFalse() {
      RuleDefinition d = definition();
      d.setEffectiveTo(LocalDateTime.now().minusDays(1));
      assertThat(d.isInEffectiveWindow(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("窗口内返回 true")
    void withinWindowReturnsTrue() {
      RuleDefinition d = definition();
      d.setEffectiveFrom(LocalDateTime.now().minusDays(1));
      d.setEffectiveTo(LocalDateTime.now().plusDays(1));
      assertThat(d.isInEffectiveWindow(LocalDateTime.now())).isTrue();
    }
  }

  @Nested
  @DisplayName("isActive 启用判定")
  class Active {

    @Test
    @DisplayName("PUBLISHED + enabled 返回 true")
    void publishedAndEnabled() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.PUBLISHED.name());
      d.setIsEnabled(true);
      assertThat(d.isActive()).isTrue();
    }

    @Test
    @DisplayName("PUBLISHED + disabled 返回 false")
    void publishedButDisabled() {
      RuleDefinition d = definition();
      d.setStatus(RuleStatus.PUBLISHED.name());
      d.setIsEnabled(false);
      assertThat(d.isActive()).isFalse();
    }

    @Test
    @DisplayName("DRAFT + enabled 返回 false")
    void draftWithEnabled() {
      assertThat(definition().isActive()).isFalse();
    }
  }

  @Nested
  @DisplayName("isCanaryEnabled 灰度判定")
  class Canary {

    @Test
    @DisplayName("canaryRatio > 0 启用灰度")
    void positiveRatioEnables() {
      RuleDefinition d = definition();
      d.setCanaryRatio(new BigDecimal("0.1"));
      assertThat(d.isCanaryEnabled()).isTrue();
    }

    @Test
    @DisplayName("canaryRatio = 0 不启用灰度")
    void zeroRatioDisables() {
      RuleDefinition d = definition();
      d.setCanaryRatio(BigDecimal.ZERO);
      assertThat(d.isCanaryEnabled()).isFalse();
    }

    @Test
    @DisplayName("canaryRatio = null 不启用灰度")
    void nullRatioDisables() {
      assertThat(definition().isCanaryEnabled()).isFalse();
    }
  }
}
