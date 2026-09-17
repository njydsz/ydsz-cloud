package com.njydsz.agent.domain.config.properties;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.agent.domain.config.properties.ProviderProperties;

/**
 * LLM 相关配置组（默认 Provider、模型、密钥、价格等）。
 *
 * <p>YAML 前缀：{@code ydsz.agent.llm}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmProperties {

  /** 默认温度 */
  private static final double DEFAULT_TEMPERATURE = 0.7;

  /** 默认最大 Token */
  private static final int DEFAULT_MAX_TOKENS = 2048;

  /** 默认调用超时（秒） */
  private static final int DEFAULT_TIMEOUT_SECONDS = 60;

  /** 默认 Provider */
  private String defaultProvider = "default";

  /** 默认模型名称 */
  private String defaultModel = "default-model";

  /** API Key */
  private String apiKey = "";

  /** API Base URL */
  private String baseUrl = "";

  /** 默认温度 */
  private double temperature = DEFAULT_TEMPERATURE;

  /** 默认最大 Token */
  private int maxTokens = DEFAULT_MAX_TOKENS;

  /** 调用超时（秒） */
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

  /** 模型单价映射（模型名 -> USD/千 Token） */
  private Map<String, Double> modelPrices = new LinkedHashMap<>(16);

  /** 未知模型兜底单价（USD/千 Token），未配置的模型使用此价格 */
  private BigDecimal fallbackPrice = new BigDecimal("0.001");

  /** 多 Provider 配置 */
  private Map<String, ProviderProperties> providers = new LinkedHashMap<>(16);
}
