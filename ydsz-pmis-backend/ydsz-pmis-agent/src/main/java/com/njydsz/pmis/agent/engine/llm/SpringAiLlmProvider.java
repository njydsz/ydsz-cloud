package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Spring AI OpenAI Provider - 真实大模型推理（批次 22 P1-1 增强版）
 *
 * <p>基于 Spring AI 1.0.0-M6 框架的 ChatClient, 兼容 1.0.0+ ChatModel 双 API.
 * 继承 {@link AbstractHttpLlmProvider} 获得:
 * <ul>
 *   <li>超时控制（默认 10s）</li>
 *   <li>重试（指数退避 2 次）</li>
 *   <li>TraceId 透传（MDC）</li>
 *   <li>失败降级（mock 兜底）</li>
 * </ul>
 *
 * <p>启用条件：
 * <ul>
 *   <li>classpath 存在 {@code org.springframework.ai.chat.ChatClient}</li>
 *   <li>Nacos 配置 {@code pmis.agent.llm.provider=spring-ai-openai}</li>
 *   <li>Nacos 配置 {@code spring.ai.openai.api-key=sk-xxx}</li>
 * </ul>
 *
 * <p>配置示例：
 * <pre>
 * pmis:
 *   agent:
 *     llm:
 *       provider: spring-ai-openai
 *       timeout-millis: 8000
 *       max-retries: 2
 *
 * spring:
 *   ai:
 *     openai:
 *       api-key: sk-xxx
 *       base-url: https://api.openai.com
 *       chat:
 *         options:
 *           model: gpt-4o-mini
 *           temperature: 0.3
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次22)
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.ai.chat.ChatClient")
@ConditionalOnProperty(prefix = "pmis.agent.llm", name = "provider", havingValue = "spring-ai-openai")
public class SpringAiLlmProvider extends AbstractHttpLlmProvider {

    /** Spring AI ChatClient 实例（可能为 null，由条件注入决定） */
    private final Object chatClient;

    public SpringAiLlmProvider(@Autowired(required = false) Object chatClient,
                               @Value("${pmis.agent.llm.timeout-millis:10000}") long timeoutMillis,
                               @Value("${pmis.agent.llm.max-retries:2}") int maxRetries,
                               @Value("${pmis.agent.llm.fallback-to-mock:true}") boolean fallback) {
        this.chatClient = chatClient;
        this.timeoutMillis = timeoutMillis;
        this.maxRetries = maxRetries;
        this.fallbackToMockOnError = fallback;
    }

    @Override
    public String name() {
        return "spring-ai-openai";
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, AgentContext context) {
        if (chatClient == null) {
            log.warn("[SpringAiLlm] ChatClient 未注入, 降级到 mock");
            return new MockLlmProvider().chat(systemPrompt, userPrompt, context);
        }
        // 拼装 user message (system + user, 兼容所有 spring-ai 版本)
        String fullPrompt = (systemPrompt == null ? "" : systemPrompt) + "\n\n" + (userPrompt == null ? "" : userPrompt);
        Callable<String> call = () -> invokeChatClient(fullPrompt);
        return executeWithGuard(call, context);
    }

    /**
     * 反射调用 ChatClient.call(String), 兼容 1.0.0-M6 + 1.0.0+ 两套 API
     */
    private String invokeChatClient(String prompt) throws Exception {
        try {
            // 优先尝试 ChatClient.call(String) - spring-ai 1.0.0-M6 API
            Object response = chatClient.getClass()
                    .getMethod("call", String.class)
                    .invoke(chatClient, prompt);
            return extractContent(response);
        } catch (NoSuchMethodException nsme) {
            // 降级: spring-ai 1.0.0+ 改用 ChatModel.call(Prompt)
            log.debug("[SpringAiLlm] ChatClient.call(String) not found, try ChatModel.call(Prompt)");
            return invokeChatModelFallback(prompt);
        }
    }

    /**
     * 降级调用 ChatModel.call(Prompt)（兼容 spring-ai 1.0.0+ API）。
     *
     * @param prompt 用户提示词
     * @return 推理结果文本
     * @throws Exception 反射调用异常
     */
    private String invokeChatModelFallback(String prompt) throws Exception {
        // 尝试 Prompt 类: org.springframework.ai.prompt.Prompt
        Class<?> promptClass = Class.forName("org.springframework.ai.prompt.Prompt");
        Object userMessage = Class.forName("org.springframework.ai.messages.UserMessage")
                .getConstructor(String.class).newInstance(prompt);
        Object promptInstance = promptClass.getConstructor(List.class)
                .newInstance(Collections.singletonList(userMessage));
        Object response = chatClient.getClass()
                .getMethod("call", promptClass)
                .invoke(chatClient, promptInstance);
        return extractContent(response);
    }

    /**
     * 从 ChatResponse 中提取文本内容。
     *
     * @param response ChatResponse 对象
     * @return 文本内容；为空返回空字符串
     * @throws Exception 反射调用异常
     */
    private String extractContent(Object response) throws Exception {
        // ChatResponse.getResult().getOutput().getContent()
        Object result = response.getClass().getMethod("getResult").invoke(response);
        Object output = result.getClass().getMethod("getOutput").invoke(result);
        Object content = output.getClass().getMethod("getContent").invoke(output);
        return content == null ? "" : content.toString();
    }
}
