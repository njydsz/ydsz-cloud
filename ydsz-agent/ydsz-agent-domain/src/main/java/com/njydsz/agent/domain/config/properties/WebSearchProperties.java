package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 网络搜索配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.web-search}，控制 Hybrid Search 中网络搜索的启用状态、
 * 搜索引擎提供商（如 DuckDuckGo）、自定义端点、API Key、超时时间与 TopK 结果数。
 * 默认不开启网络搜索（isEnabled=false），使用 DuckDuckGo，超时 10 秒，返回 Top 5 结果。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchProperties {
  private static final int DEFAULT_TIMEOUT_SECONDS = 10;
  private static final int DEFAULT_TOP_K = 5;

  private boolean isEnabled = false;
  private String provider = "duckduckgo";
  private String endpoint = "";
  private String apiKey = "";
  private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
  private int topK = DEFAULT_TOP_K;
  private boolean isDegradedOnFailure = true;
}
