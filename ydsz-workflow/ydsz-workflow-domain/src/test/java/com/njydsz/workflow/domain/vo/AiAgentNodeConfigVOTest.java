package com.njydsz.workflow.domain.vo;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.njydsz.workflow.domain.vo.AiAgentNodeConfigVO.FallbackStrategy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AiAgentNodeConfigVO} 单元测试。
 *
 * <p>覆盖：fromExt 解析（默认值/agentId/promptTemplate/兜底策略/重试/超时）、hasValidAgentId、isRetryFallback、fromExtJson 容错。
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@DisplayName("AI 审批节点配置值对象")
class AiAgentNodeConfigVOTest {

  @Nested
  @DisplayName("fromExt 解析")
  class FromExt {

    @Test
    @DisplayName("null Map 使用默认值")
    void nullMapUsesDefaults() {
      AiAgentNodeConfigVO config = AiAgentNodeConfigVO.fromExt(null);
      assertThat(config.getAgentId()).isEmpty();
      assertThat(config.getPromptTemplate()).isEmpty();
      assertThat(config.getOutputSchema()).isEmpty();
      assertThat(config.getFallbackStrategy()).isEqualTo(FallbackStrategy.AUTO_PASS);
      assertThat(config.getRetryMax()).isEqualTo(1);
      assertThat(config.getTimeoutMs()).isEqualTo(30000);
    }

    @Test
    @DisplayName("解析完整配置")
    void fullConfig() {
      AiAgentNodeConfigVO config =
          AiAgentNodeConfigVO.fromExt(
              Map.of(
                  "agentId", "agent-1",
                  "promptTemplate", "审批 ${amount}",
                  "outputSchema", "{\"type\":\"object\"}",
                  "fallbackStrategy", "TRANSFER_ADMIN",
                  "retryMax", 3,
                  "timeoutMs", 5000));
      assertThat(config.getAgentId()).isEqualTo("agent-1");
      assertThat(config.getPromptTemplate()).isEqualTo("审批 ${amount}");
      assertThat(config.getOutputSchema()).isEqualTo("{\"type\":\"object\"}");
      assertThat(config.getFallbackStrategy()).isEqualTo(FallbackStrategy.TRANSFER_ADMIN);
      assertThat(config.getRetryMax()).isEqualTo(3);
      assertThat(config.getTimeoutMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("retryMax ≤0 时回退默认值")
    void retryMaxMin() {
      assertThat(
              AiAgentNodeConfigVO.fromExt(Map.of("retryMax", 0)).getRetryMax())
          .isEqualTo(1);
    }

    @Test
    @DisplayName("timeoutMs ≤0 时回退默认值")
    void timeoutMin() {
      assertThat(
              AiAgentNodeConfigVO.fromExt(Map.of("timeoutMs", -1)).getTimeoutMs())
          .isEqualTo(30000);
    }

    @Test
    @DisplayName("非法兜底策略回退 AUTO_PASS")
    void invalidFallbackFallsBack() {
      assertThat(
              AiAgentNodeConfigVO.fromExt(Map.of("fallbackStrategy", "BOGUS"))
                  .getFallbackStrategy())
          .isEqualTo(FallbackStrategy.AUTO_PASS);
    }
  }

  @Nested
  @DisplayName("hasValidAgentId 判定")
  class ValidAgentId {

    @Test
    @DisplayName("非空 agentId 返回 true")
    void validAgentId() {
      assertThat(
              AiAgentNodeConfigVO.fromExt(Map.of("agentId", "agent-1")).hasValidAgentId())
          .isTrue();
    }

    @Test
    @DisplayName("空 agentId 返回 false")
    void emptyAgentId() {
      assertThat(AiAgentNodeConfigVO.fromExt(null).hasValidAgentId()).isFalse();
    }

    @Test
    @DisplayName("空白 agentId 返回 false")
    void blankAgentId() {
      assertThat(
              AiAgentNodeConfigVO.fromExt(Map.of("agentId", "  ")).hasValidAgentId())
          .isFalse();
    }
  }

  @Nested
  @DisplayName("isRetryFallback 判定")
  class RetryFallback {

    @Test
    @DisplayName("RETRY 策略返回 true")
    void retryStrategy() {
      assertThat(
              AiAgentNodeConfigVO.fromExt(Map.of("fallbackStrategy", "RETRY"))
                  .isRetryFallback())
          .isTrue();
    }

    @Test
    @DisplayName("AUTO_PASS 策略返回 false")
    void autoPassStrategy() {
      assertThat(
              AiAgentNodeConfigVO.fromExt(Map.of("fallbackStrategy", "AUTO_PASS"))
                  .isRetryFallback())
          .isFalse();
    }
  }

  @Nested
  @DisplayName("fromExtJson 容错")
  class FromExtJson {

    @Test
    @DisplayName("非法 JSON 容错")
    void invalidJson() {
      assertThat(AiAgentNodeConfigVO.fromExtJson("bad").getAgentId()).isEmpty();
    }

    @Test
    @DisplayName("合法 JSON 解析正确")
    void validJson() {
      AiAgentNodeConfigVO config =
          AiAgentNodeConfigVO.fromExtJson("{\"agentId\":\"a1\",\"timeoutMs\":1000}");
      assertThat(config.getAgentId()).isEqualTo("a1");
      assertThat(config.getTimeoutMs()).isEqualTo(1000);
    }
  }
}
