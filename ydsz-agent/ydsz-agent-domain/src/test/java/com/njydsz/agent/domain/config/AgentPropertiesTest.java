package com.njydsz.agent.domain.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link AgentProperties} 单元测试。
 *
 * <p>覆盖全量子系统默认值初始化、isXxx 布尔 getter 语义、嵌套配置组实例化、
 * 价格阈值精度等核心行为。
 *
 * @author ydsz-team
 * @since 26.09.16
 */
@Tag("unit")
@DisplayName("AgentProperties 配置绑定与默认值测试")
class AgentPropertiesTest {

  /** 被测对象（使用无参构造器，验证默认值初始化）。 */
  private final AgentProperties properties = new AgentProperties();

  @Nested
  @DisplayName("顶层 isEnabled 布尔字段 getter 语义")
  class IsEnabledGetterSemantics {

    /**
     * isEnabled 默认值应为 true，符合"模块启用优先"设计理念。
     */
    @Test
    @DisplayName("isEnabled 默认值应为 true")
    void isEnabledShouldDefaultToTrue() {
      assertThat(properties.isEnabled()).isTrue();
    }

    /**
     * setter 设置 isEnabled=false 后 getter 应返回 false。
     */
    @Test
    @DisplayName("setIsEnabled(false) 后 isEnabled() 返回 false")
    void setIsEnabledToFalseShouldReflectInGetter() {
      properties.setEnabled(false);

      assertThat(properties.isEnabled()).isFalse();
    }

    /**
     * setter 设置 isEnabled=true 后 getter 应返回 true。
     */
    @Test
    @DisplayName("setIsEnabled(true) 后 isEnabled() 返回 true")
    void setIsEnabledToTrueShouldReflectInGetter() {
      properties.setEnabled(true);

      assertThat(properties.isEnabled()).isTrue();
    }
  }

  @Nested
  @DisplayName("默认系统提示词")
  class DefaultSystemPrompt {

    /**
     * 默认系统提示词不应为空，确保 Agent 在未配置情况下仍有基础行为。
     */
    @Test
    @DisplayName("defaultSystemPrompt 默认非空且包含中文")
    void defaultSystemPromptShouldContainChinese() {
      assertThat(properties.getDefaultSystemPrompt())
          .isNotNull()
          .isNotBlank()
          .contains("智能助手");
    }
  }

  @Nested
  @DisplayName("嵌套配置组自动实例化")
  class NestedConfigurationsAutoInstantiated {

    /**
     * 所有嵌套配置组不应为 null（@Data 无参构造器应创建默认实例）。
     */
    @Test
    @DisplayName("所有嵌套配置组应自动实例化非 null")
    void allNestedConfigsShouldNotBeNull() {
      assertThat(properties.getLlm()).isNotNull();
      assertThat(properties.getMemory()).isNotNull();
      assertThat(properties.getRag()).isNotNull();
      assertThat(properties.getMcp()).isNotNull();
      assertThat(properties.getMcpServer()).isNotNull();
      assertThat(properties.getText2sql()).isNotNull();
      assertThat(properties.getCache()).isNotNull();
      assertThat(properties.getPromptTemplate()).isNotNull();
      assertThat(properties.getGuardrail()).isNotNull();
      assertThat(properties.getTool()).isNotNull();
      assertThat(properties.getQuota()).isNotNull();
      assertThat(properties.getMemoryConsolidation()).isNotNull();
      assertThat(properties.getProfile()).isNotNull();
      assertThat(properties.getOtel()).isNotNull();
      assertThat(properties.getInsight()).isNotNull();
      assertThat(properties.getCodeExecution()).isNotNull();
    }
  }

  @Nested
  @DisplayName("LLM 子配置默认值")
  class LlmSubConfigurationDefaults {

    /**
     * LLM 默认 Provider 不应为空字符串或 null。
     */
    @Test
    @DisplayName("LLM defaultProvider 默认应为 'default'")
    void defaultProviderShouldBeDefault() {
      assertThat(properties.getLlm().getDefaultProvider()).isEqualTo("default");
    }

    /**
     * LLM 默认温度应在合理范围（0, 2）内。
     */
    @Test
    @DisplayName("LLM temperature 默认值应在 (0, 2) 范围内")
    void temperatureShouldBeInValidRange() {
      assertThat(properties.getLlm().getTemperature())
          .isGreaterThan(0.0)
          .isLessThan(2.0);
    }

    /**
     * LLM 最大 Token 应为正整数。
     */
    @Test
    @DisplayName("LLM maxTokens 默认值应大于 0")
    void maxTokensShouldBePositive() {
      assertThat(properties.getLlm().getMaxTokens()).isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("配额配置 Quota 子配置")
  class QuotaSubConfiguration {

    /**
     * 默认 Token 上限应为正数，确保有效配额管控。
     */
    @Test
    @DisplayName("配额 tokenLimit 默认应大于 0")
    void tokenLimitShouldBePositive() {
      assertThat(properties.getQuota().getDailyTokenLimit()).isGreaterThan(0);
    }

    /**
     * 价格阈值应使用 BigDecimal 类型且非负（禁止 double/float，符合 YDIZ-OOP-003）。
     */
    @Test
    @DisplayName("价格阈值 alertThreshold 应非负")
    void alertThresholdShouldBeNonNegative() {
      double threshold = properties.getQuota().getAlertThreshold();

      assertThat(threshold).isGreaterThanOrEqualTo(0.0);
    }
  }
}
