package com.njydsz.agent.domain.config.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reranker 重排序配置属性。
 *
 * <p>绑定配置前缀 {@code ydzs.agent.reranker}，控制检索后精排环节的服务端点、API Key、
 * 模型名称与超时时间。默认不开启（isEnabled=false），使用 bge-reranker-v2-m3 模型，
 * 超时 5000 毫秒，启用后可提升 TopK 结果的相关性。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RerankerProperties {
  private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

  private boolean isEnabled = false;
  private String baseUrl = "";
  private String apiKey = "";
  private String model = "bge-reranker-v2-m3";
  private int timeoutMillis = DEFAULT_TIMEOUT_MILLIS;
}
