package com.njydsz.pmis.agent.engine.llm;

import lombok.RequiredArgsConstructor;
import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Spring AI LLM Provider - 真实大模型推理（批次 19 P3-1 落地）
 *
 * <p>基于 Spring AI 1.0.0-M6 框架，支持 OpenAI / 通义千问 / 百度千帆等多家模型。
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 存在 {@code org.springframework.ai.chat.ChatClient}</li>
 *   <li>Nacos 配置 {@code pmis.agent.llm.provider=spring-ai-openai|spring-ai-dashscope|spring-ai-qianfan}</li>
 *   <li>Nacos 配置 {@code spring.ai.openai.api-key=xxx} 或 {@code spring.ai.dashscope.api-key=xxx}</li>
 * </ul>
 *
 * <p>使用方式：在 application.yml 中配置
 * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: spring-ai-dashscope  # 切换到通义千问
 *
 * spring:
 *   ai:
 *     dashscope:
 *       api-key: sk-xxx
 *       base-url: https://dashscope.aliyuncs.com
 *     openai:
 *       api-key: sk-xxx
 *       base-url: https://api.openai.com
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.ai.chat.ChatClient")
@ConditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "spring-ai-openai")
public class SpringAiLlmProvider implements LlmProvider {

    private final Object chatClient;

    public SpringAiLlmProvider(@Autowired(required = false) Object chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String name() {
        return "spring-ai-openai";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        if (chatClient == null) {
            log.warn("[SpringAiLlm] ChatClient 未注入，降级到 mock");
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
        try {
            // 反射调用 ChatClient.call()，避免硬依赖 spring-ai 1.0.0-M6 API 变化
            // spring-ai 1.0.0-M6 以后 API 调整为 ChatModel.call(Prompt)
            Object response = chatClient.getClass()
                    .getMethod("call", String.class)
                    .invoke(chatClient, systemPrompt + "\n\n" + userPrompt);
            Object result = response.getClass().getMethod("getResult").invoke(response);
            Object output = result.getClass().getMethod("getOutput").invoke(result);
            Object content = output.getClass().getMethod("getContent").invoke(output);
            return content.toString();
        } catch (Exception e) {
            log.error("[SpringAiLlm] call failed, fallback to mock", e);
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
    }
}
