package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SpringAiLlmProvider 单元测试
 *
 * <p>不依赖真实 Spring AI 类，全部使用自定义 stub 对象。
 * 覆盖：
 * <ul>
 *   <li>chatClient=null 时降级到 MockLlmProvider</li>
 *   <li>chatClient 非 null 时反射调用 call(String) 成功</li>
 *   <li>反射调用 call(String) 抛 NoSuchMethodException 时降级到 invokeChatModelFallback 路径</li>
 *   <li>extractContent 抛异常时降级到 mock</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("SpringAiLlmProvider Spring AI LLM Provider 测试")
class SpringAiLlmProviderTest {

    // ==================== Stub 类（模拟 Spring AI ChatClient / ChatResponse 链） ====================

    /** 模拟 ChatClient - 有 call(String) 方法 */
    public static class StubChatClient {
        public Object callResponse;
        public Object call(String prompt) {
            return callResponse;
        }
    }

    /** 模拟 ChatResponse - 有 getResult() 方法 */
    public static class StubChatResponse {
        public Object generation;
        public Object getResult() {
            return generation;
        }
    }

    /** 模拟 Generation - 有 getOutput() 方法 */
    public static class StubGeneration {
        public Object output;
        public Object getOutput() {
            return output;
        }
    }

    /** 模拟 Output - 有 getContent() 方法 */
    public static class StubOutput {
        public String content;
        public String getContent() {
            return content;
        }
    }

    /** 模拟无 call(String) 方法的对象（触发 NoSuchMethodException） */
    public static class StubNoCallStringClient {
        public Object callResponse;
        // 没有 call(String) 方法，会触发 NoSuchMethodException
    }

    /** 模拟返回非法响应的对象（extractContent 抛 NoSuchMethodException） */
    public static class StubBadResponseClient {
        public Object call(String prompt) {
            return new Object();  // Object 没有 getResult() 方法
        }
    }

    // ==================== 辅助方法 ====================

    /** 构造完整的 chat response 链：response.getResult().getOutput().getContent() */
    private StubChatClient clientWithContent(String content) {
        StubOutput output = new StubOutput();
        output.content = content;
        StubGeneration generation = new StubGeneration();
        generation.output = output;
        StubChatResponse response = new StubChatResponse();
        response.generation = generation;
        StubChatClient client = new StubChatClient();
        client.callResponse = response;
        return client;
    }

    // ==================== chatClient=null 测试 ====================

    @Nested
    @DisplayName("chatClient=null 降级测试")
    class NullChatClientTest {

        @Test
        @DisplayName("chatClient=null 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenChatClientNull() {
            SpringAiLlmProvider provider = new SpringAiLlmProvider(null, 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            // MockLlmProvider 对普通查询返回 NORMAL 等级
            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("chatClient=null 时返回 MockLlmProvider 的标准输出")
        void shouldReturnMockOutputWhenChatClientNull() {
            SpringAiLlmProvider provider = new SpringAiLlmProvider(null, 5000, 0, true);

            // 触发"严重"关键词，MockLlmProvider 返回 RED
            String result = provider.chat("sys", "严重告警", new AgentContext());

            assertThat(result).contains("RED");
        }
    }

    // ==================== 反射调用成功测试 ====================

    @Nested
    @DisplayName("反射调用成功测试")
    class ReflectionSuccessTest {

        @Test
        @DisplayName("chatClient 非 null 时反射调用 call(String) 成功")
        void shouldCallViaReflection() {
            StubChatClient chatClient = clientWithContent("hello from spring-ai");
            SpringAiLlmProvider provider = new SpringAiLlmProvider(chatClient, 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("hello from spring-ai");
        }

        @Test
        @DisplayName("systemPrompt=null 时不抛 NPE")
        void shouldHandleNullSystemPrompt() {
            StubChatClient chatClient = clientWithContent("ok");
            SpringAiLlmProvider provider = new SpringAiLlmProvider(chatClient, 5000, 0, true);

            String result = provider.chat(null, "user", new AgentContext());

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("userPrompt=null 时不抛 NPE")
        void shouldHandleNullUserPrompt() {
            StubChatClient chatClient = clientWithContent("ok");
            SpringAiLlmProvider provider = new SpringAiLlmProvider(chatClient, 5000, 0, true);

            String result = provider.chat("sys", null, new AgentContext());

            assertThat(result).isEqualTo("ok");
        }

        @Test
        @DisplayName("getContent() 返回 null 时返回空字符串")
        void shouldReturnEmptyStringWhenContentNull() {
            StubOutput output = new StubOutput();
            output.content = null;
            StubGeneration generation = new StubGeneration();
            generation.output = output;
            StubChatResponse response = new StubChatResponse();
            response.generation = generation;
            StubChatClient chatClient = new StubChatClient();
            chatClient.callResponse = response;

            SpringAiLlmProvider provider = new SpringAiLlmProvider(chatClient, 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            assertThat(result).isEqualTo("");
        }
    }

    // ==================== NoSuchMethodException 降级测试 ====================

    @Nested
    @DisplayName("NoSuchMethodException 降级测试")
    class NoSuchMethodTest {

        @Test
        @DisplayName("call(String) 不存在时降级到 invokeChatModelFallback 路径（最终降级到 mock）")
        void shouldFallbackToInvokeChatModelFallback() {
            // StubNoCallStringClient 没有 call(String) 方法，会触发 NoSuchMethodException
            // invokeChatModelFallback 会尝试加载 Spring AI 类，若类不在或调用失败，
            // executeWithGuard 会重试并最终降级到 mock
            StubNoCallStringClient chatClient = new StubNoCallStringClient();
            SpringAiLlmProvider provider = new SpringAiLlmProvider(chatClient, 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            // 最终降级到 MockLlmProvider 的输出
            assertThat(result).contains("NORMAL");
        }
    }

    // ==================== extractContent 异常测试 ====================

    @Nested
    @DisplayName("extractContent 异常降级测试")
    class ExtractContentExceptionTest {

        @Test
        @DisplayName("extractContent 抛异常时降级到 mock")
        void shouldFallbackToMockWhenExtractContentFails() {
            // StubBadResponseClient.call(String) 返回普通 Object，没有 getResult() 方法
            // extractContent 会抛 NoSuchMethodException
            StubBadResponseClient chatClient = new StubBadResponseClient();
            SpringAiLlmProvider provider = new SpringAiLlmProvider(chatClient, 5000, 0, true);

            String result = provider.chat("sys", "user", new AgentContext());

            // 降级到 MockLlmProvider 的输出
            assertThat(result).contains("NORMAL");
        }

        @Test
        @DisplayName("extractContent 抛异常且 fallbackToMockOnError=false 时抛 RuntimeException")
        void shouldThrowWhenExtractContentFailsAndFallbackDisabled() {
            StubBadResponseClient chatClient = new StubBadResponseClient();
            SpringAiLlmProvider provider = new SpringAiLlmProvider(chatClient, 5000, 0, false);

            assertThatThrownBy(() -> provider.chat("sys", "user", new AgentContext()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("spring-ai-openai")
                    .hasMessageContaining("failed");
        }
    }

    // ==================== name() 测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("name() 返回 'spring-ai-openai'")
        void shouldReturnSpringAiOpenAiName() {
            SpringAiLlmProvider provider = new SpringAiLlmProvider(null, 5000, 0, true);
            assertThat(provider.name()).isEqualTo("spring-ai-openai");
        }
    }
}
