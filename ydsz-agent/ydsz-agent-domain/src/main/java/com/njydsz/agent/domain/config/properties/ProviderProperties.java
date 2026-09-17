package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个 Provider 配置（名称、模型、API 密钥、Base URL 等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderProperties {

  /** Provider 名称 */
  private String name;

  /** Provider 类型 (openai / deepseek 等) */
  private String type;

  /** 默认模型名称 */
  private String model;

  /** API Key */
  private String apiKey;

  /** API Base URL */
  private String baseUrl;

  /** 温度 */
  private Double temperature;

  /** 最大 Token */
  private Integer maxTokens;

  /** 调用超时（秒） */
  private Integer timeoutSeconds;

  /** 是否启用 */
  private boolean isEnabled = true;
}
