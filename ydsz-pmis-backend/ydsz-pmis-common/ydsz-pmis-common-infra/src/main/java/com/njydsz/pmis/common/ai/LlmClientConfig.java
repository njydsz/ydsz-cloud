package com.njydsz.pmis.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 客户端统一配置（P0-2 架构优化）。
 *
 * <p>统一管理 LLM 相关配置，替代 literule 和 agent 各自维护的配置项。
 * 配置前缀：{@code pmis.common.ai}
 *
 * @author ydsz-pmis-team
 * @since 1.6.0 (P0-2)
 */
@Data
@ConfigurationProperties(prefix = "pmis.common.ai")
public class LlmClientConfig {

    /** LLM 客户端类型：MOCK / OPENAI_COMPATIBLE */
    private String clientType = "MOCK";

    /** LLM API URL（OpenAI 兼容协议端点） */
    private String apiUrl;

    /** LLM API Key */
    private String apiKey;

    /** 模型名称 */
    private String model = "gpt-4o-mini";

    /** 温度参数（0-2，越低越确定） */
    private double temperature = 0.2;

    /** 调用超时（毫秒） */
    private long timeoutMs = 30000;

    /** 是否启用流式输出 */
    private boolean streamingEnabled = false;

    /** 是否启用 Function Calling */
    private boolean functionCallingEnabled = false;
}
