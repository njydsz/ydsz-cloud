package com.njydsz.workflow.domain.vo;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.enums.FlowSlaAction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SlaConfigVO} 单元测试。
 *
 * <p>覆盖：fromExt 解析（默认值/超时/动作/升级用户）、isTimeoutEnabled、isTerminalAction、hasEscalation、fromExtJson 容错。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("SLA 超时配置值对象")
class SlaConfigVOTest {

  @Nested
  @DisplayName("fromExt 解析")
  class FromExt {

    @Test
    @DisplayName("null Map 使用默认值（超时关闭）")
    void nullMapUsesDefaults() {
      SlaConfigVO config = SlaConfigVO.fromExt(null);
      assertThat(config.getTimeoutMinutes()).isZero();
      assertThat(config.getAction()).isEqualTo(FlowSlaAction.REMIND);
      assertThat(config.getEscalateUserId()).isEmpty();
    }

    @Test
    @DisplayName("解析超时分钟数与动作")
    void timeoutAndAction() {
      SlaConfigVO config =
          SlaConfigVO.fromExt(Map.of("timeout", 60, "timeoutStrategy", "AUTO_PASS"));
      assertThat(config.getTimeoutMinutes()).isEqualTo(60);
      assertThat(config.getAction()).isEqualTo(FlowSlaAction.AUTO_PASS);
    }

    @Test
    @DisplayName("解析 ESCALATE 与 escalateUser")
    void escalate() {
      SlaConfigVO config =
          SlaConfigVO.fromExt(
              Map.of("timeoutStrategy", "ESCALATE", "escalateUser", "admin-1"));
      assertThat(config.getAction()).isEqualTo(FlowSlaAction.ESCALATE);
      assertThat(config.getEscalateUserId()).isEqualTo("admin-1");
    }

    @Test
    @DisplayName("timeout ≤0 时强制为 0")
    void timeoutMinZero() {
      SlaConfigVO config = SlaConfigVO.fromExt(Map.of("timeout", -10));
      assertThat(config.getTimeoutMinutes()).isZero();
    }

    @Test
    @DisplayName("非法动作名回退 REMIND")
    void invalidActionFallsBack() {
      SlaConfigVO config = SlaConfigVO.fromExt(Map.of("timeoutStrategy", "BOGUS"));
      assertThat(config.getAction()).isEqualTo(FlowSlaAction.REMIND);
    }
  }

  @Nested
  @DisplayName("fromExtJson 容错")
  class FromExtJson {

    @Test
    @DisplayName("null/空白使用默认值")
    void nullOrBlank() {
      assertThat(SlaConfigVO.fromExtJson(null).getTimeoutMinutes()).isZero();
      assertThat(SlaConfigVO.fromExtJson("  ").getAction()).isEqualTo(FlowSlaAction.REMIND);
    }

    @Test
    @DisplayName("非法 JSON 容错")
    void invalidJson() {
      assertThat(SlaConfigVO.fromExtJson("bad").getAction()).isEqualTo(FlowSlaAction.REMIND);
    }

    @Test
    @DisplayName("合法 JSON 解析正确")
    void validJson() {
      SlaConfigVO config = SlaConfigVO.fromExtJson("{\"timeout\":30,\"timeoutStrategy\":\"AUTO_REJECT\"}");
      assertThat(config.getTimeoutMinutes()).isEqualTo(30);
      assertThat(config.getAction()).isEqualTo(FlowSlaAction.AUTO_REJECT);
    }
  }

  @Nested
  @DisplayName("isTimeoutEnabled 超时启用判定")
  class TimeoutEnabled {

    @Test
    @DisplayName("timeout > 0 启用")
    void positiveTimeout() {
      assertThat(SlaConfigVO.fromExt(Map.of("timeout", 1)).isTimeoutEnabled()).isTrue();
    }

    @Test
    @DisplayName("timeout = 0 不启用")
    void zeroTimeout() {
      assertThat(SlaConfigVO.fromExt(Map.of("timeout", 0)).isTimeoutEnabled()).isFalse();
    }
  }

  @Nested
  @DisplayName("isTerminalAction 终态动作判定")
  class TerminalAction {

    @Test
    @DisplayName("ESCALATE / AUTO_PASS / AUTO_REJECT 为终态")
    void terminalActions() {
      assertThat(
              SlaConfigVO.fromExt(Map.of("timeoutStrategy", "ESCALATE")).isTerminalAction())
          .isTrue();
      assertThat(
              SlaConfigVO.fromExt(Map.of("timeoutStrategy", "AUTO_PASS")).isTerminalAction())
          .isTrue();
      assertThat(
              SlaConfigVO.fromExt(Map.of("timeoutStrategy", "AUTO_REJECT")).isTerminalAction())
          .isTrue();
    }

    @Test
    @DisplayName("REMIND / NOTIFY 非终态")
    void nonTerminalActions() {
      assertThat(
              SlaConfigVO.fromExt(Map.of("timeoutStrategy", "REMIND")).isTerminalAction())
          .isFalse();
      assertThat(
              SlaConfigVO.fromExt(Map.of("timeoutStrategy", "NOTIFY")).isTerminalAction())
          .isFalse();
    }
  }

  @Nested
  @DisplayName("hasEscalation 升级判定")
  class HasEscalation {

    @Test
    @DisplayName("ESCALATE + 非空用户 → true")
    void escalateWithUser() {
      assertThat(
              SlaConfigVO.fromExt(
                      Map.of("timeoutStrategy", "ESCALATE", "escalateUser", "u1"))
                  .hasEscalation())
          .isTrue();
    }

    @Test
    @DisplayName("ESCALATE + 空用户 → false")
    void escalateWithoutUser() {
      assertThat(
              SlaConfigVO.fromExt(Map.of("timeoutStrategy", "ESCALATE"))
                  .hasEscalation())
          .isFalse();
    }

    @Test
    @DisplayName("非 ESCALATE → false")
    void nonEscalate() {
      assertThat(
              SlaConfigVO.fromExt(Map.of("timeoutStrategy", "AUTO_PASS"))
                  .hasEscalation())
          .isFalse();
    }
  }
}
