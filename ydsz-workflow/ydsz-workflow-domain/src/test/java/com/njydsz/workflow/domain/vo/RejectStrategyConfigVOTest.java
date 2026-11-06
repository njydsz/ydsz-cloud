package com.njydsz.workflow.domain.vo;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.vo.RejectStrategyConfigVO.ReExecuteMode;
import com.njydsz.workflow.domain.vo.RejectStrategyConfigVO.RejectStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RejectStrategyConfigVO} 单元测试。
 *
 * <p>覆盖：fromExt 解析（默认值/策略/允许列表/自定义目标）、isAllowed、requiresCustomTarget、getEffectiveTarget、fromExtJson 容错。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("节点驳回策略配置值对象")
class RejectStrategyConfigVOTest {

  @Nested
  @DisplayName("fromExt 解析")
  class FromExt {

    @Test
    @DisplayName("null Map 使用默认值")
    void nullMapUsesDefaults() {
      RejectStrategyConfigVO config = RejectStrategyConfigVO.fromExt(null);
      assertThat(config.getRejectStrategy()).isEqualTo(RejectStrategy.PREVIOUS);
      assertThat(config.getReExecuteMode()).isEqualTo(ReExecuteMode.RETURN);
      assertThat(config.getAllowedStrategies())
          .containsExactly(RejectStrategy.PREVIOUS, RejectStrategy.INITIATOR);
      assertThat(config.getCustomTarget()).isEmpty();
    }

    @Test
    @DisplayName("空 Map 使用默认值")
    void emptyMapUsesDefaults() {
      RejectStrategyConfigVO config = RejectStrategyConfigVO.fromExt(Map.of());
      assertThat(config.getRejectStrategy()).isEqualTo(RejectStrategy.PREVIOUS);
    }

    @Test
    @DisplayName("解析 CUSTOM 策略与 customTarget")
    void customStrategy() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(
              Map.of("rejectStrategy", "CUSTOM", "customTarget", "node-5"));
      assertThat(config.getRejectStrategy()).isEqualTo(RejectStrategy.CUSTOM);
      assertThat(config.getCustomTarget()).isEqualTo("node-5");
    }

    @Test
    @DisplayName("解析 allowedStrategies 列表")
    void allowedStrategies() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(
              Map.of("allowedStrategies", List.of("PREVIOUS", "ANY_NODE")));
      assertThat(config.getAllowedStrategies())
          .containsExactly(RejectStrategy.PREVIOUS, RejectStrategy.ANY_NODE);
    }

    @Test
    @DisplayName("allowedStrategies 含非法值时过滤")
    void allowedStrategiesFiltersInvalid() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(
              Map.of("allowedStrategies", List.of("PREVIOUS", "BOGUS", "INITIATOR")));
      assertThat(config.getAllowedStrategies())
          .containsExactly(RejectStrategy.PREVIOUS, RejectStrategy.INITIATOR);
    }

    @Test
    @DisplayName("allowedStrategies 为空列表时回退默认值")
    void emptyAllowedFallsBack() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(Map.of("allowedStrategies", List.of()));
      assertThat(config.getAllowedStrategies())
          .containsExactly(RejectStrategy.PREVIOUS, RejectStrategy.INITIATOR);
    }

    @Test
    @DisplayName("非法策略名回退 PREVIOUS")
    void invalidStrategyFallsBack() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(Map.of("rejectStrategy", "BOGUS"));
      assertThat(config.getRejectStrategy()).isEqualTo(RejectStrategy.PREVIOUS);
    }

    @Test
    @DisplayName("策略名大小写不敏感")
    void caseInsensitive() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(Map.of("rejectStrategy", "custom"));
      assertThat(config.getRejectStrategy()).isEqualTo(RejectStrategy.CUSTOM);
    }

    @Test
    @DisplayName("reExecuteMode 解析 CONTINUE")
    void reExecuteMode() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(Map.of("reExecuteMode", "CONTINUE"));
      assertThat(config.getReExecuteMode()).isEqualTo(ReExecuteMode.CONTINUE);
    }
  }

  @Nested
  @DisplayName("fromExtJson 容错")
  class FromExtJson {

    @Test
    @DisplayName("null/空白使用默认值")
    void nullOrBlank() {
      assertThat(RejectStrategyConfigVO.fromExtJson(null).getRejectStrategy())
          .isEqualTo(RejectStrategy.PREVIOUS);
      assertThat(RejectStrategyConfigVO.fromExtJson("  ").getRejectStrategy())
          .isEqualTo(RejectStrategy.PREVIOUS);
    }

    @Test
    @DisplayName("非法 JSON 容错")
    void invalidJson() {
      RejectStrategyConfigVO config = RejectStrategyConfigVO.fromExtJson("not-json");
      assertThat(config.getRejectStrategy()).isEqualTo(RejectStrategy.PREVIOUS);
    }

    @Test
    @DisplayName("合法 JSON 解析正确")
    void validJson() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExtJson("{\"rejectStrategy\":\"ANY_NODE\"}");
      assertThat(config.getRejectStrategy()).isEqualTo(RejectStrategy.ANY_NODE);
    }
  }

  @Nested
  @DisplayName("isAllowed 策略判定")
  class IsAllowed {

    @Test
    @DisplayName("默认允许 PREVIOUS 与 INITIATOR")
    void defaultAllowed() {
      RejectStrategyConfigVO config = RejectStrategyConfigVO.fromExt(Map.of());
      assertThat(config.isAllowed(RejectStrategy.PREVIOUS)).isTrue();
      assertThat(config.isAllowed(RejectStrategy.INITIATOR)).isTrue();
      assertThat(config.isAllowed(RejectStrategy.ANY_NODE)).isFalse();
    }

    @Test
    @DisplayName("null 策略返回 false")
    void nullStrategyFalse() {
      assertThat(RejectStrategyConfigVO.fromExt(Map.of()).isAllowed(null)).isFalse();
    }
  }

  @Nested
  @DisplayName("requiresCustomTarget / getEffectiveTarget")
  class CustomTarget {

    @Test
    @DisplayName("CUSTOM 策略需要自定义目标")
    void customNeedsTarget() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(
              Map.of("rejectStrategy", "CUSTOM", "customTarget", "node-9"));
      assertThat(config.requiresCustomTarget()).isTrue();
      assertThat(config.getEffectiveTarget()).isEqualTo("node-9");
    }

    @Test
    @DisplayName("非 CUSTOM 策略不需要自定义目标")
    void nonCustomNoTarget() {
      RejectStrategyConfigVO config =
          RejectStrategyConfigVO.fromExt(Map.of("rejectStrategy", "PREVIOUS"));
      assertThat(config.requiresCustomTarget()).isFalse();
      assertThat(config.getEffectiveTarget()).isEmpty();
    }
  }
}
