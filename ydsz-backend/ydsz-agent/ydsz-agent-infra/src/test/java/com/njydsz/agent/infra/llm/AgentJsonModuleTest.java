package com.njydsz.agent.infra.llm;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.njydsz.agent.domain.json.AgentJsonModule;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.module.JsonModuleRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentJsonModule 单元测试。
 *
 * <p>验证 P1-1 核心修复：JsonModule 注册中心此前在序列化/反序列化真实路径中从未被查询
 * （形同死代码），现已在 SerializationProvider / DeserializationProvider 接入，
 * JsonModule.SpringFactory 注册的序列化器/反序列化器可真正在全局 toJson/toObject 路径中生效。</p>
 *
 * <p>本测试直接操作 {@link JsonModuleRegistry} 单例注册模块（不依赖 Spring 上下文），
 * 覆盖序列化（ChatMessage / ToolCall / ChatRequest 的 OpenAI 契约形状）与反序列化
 * （TokenUsage / ToolCall 经新版 {@code deserializer.JsonDeserializer} 接口回环）。</p>
 */
class AgentJsonModuleTest {

    @BeforeAll
    static void registerModule() {
        // 清空单例注册中心（避免跨测试类干扰），重新注册 AgentJsonModule 并初始化
        JsonModuleRegistry registry = JsonModuleRegistry.getInstance();
        registry.clear();
        registry.registerModule(new AgentJsonModule());
        registry.initialize();
    }

    @Test
    void chatMessage_serializesToOpenAiShape_excludesInternalFields() {
        ChatMessage msg = ChatMessage.user("hello", "conv-1");
        String json = YdszJson.toJson(msg);

        assertTrue(json.contains("\"role\":\"user\""), "role 应为 API 枚举值 user");
        assertTrue(json.contains("\"content\":\"hello\""), "content 应输出");
        assertFalse(json.contains("conversationId"), "不应输出内部字段 conversationId");
        assertFalse(json.contains("createdAt"), "不应输出内部字段 createdAt");
    }

    @Test
    void toolCall_serializesArgumentsAsJsonString() {
        ToolCall tc = new ToolCall("call-1", "getWeather", Map.of("city", "Beijing"));
        String json = YdszJson.toJson(tc);

        assertTrue(json.contains("\"type\":\"function\""), "type 应为 function");
        assertTrue(json.contains("\"name\":\"getWeather\""), "function.name 应输出");
        // arguments 按 OpenAI 契约序列化为 JSON 字符串（含转义引号）
        assertTrue(json.contains("\"arguments\":\"{\\\"city\\\":\\\"Beijing\\\"}\""),
                "arguments 应为 JSON 字符串: " + json);
    }

    @Test
    void chatRequest_serializesToOpenAiRequestBody() {
        ChatMessage msg = ChatMessage.system("You are a helpful assistant.");
        ChatRequest req = ChatRequest.builder()
                .model("gpt-4o")
                .messages(List.of(msg))
                .temperature(0.7)
                .maxTokens(2048)
                .build();
        String json = YdszJson.toJson(req);

        assertTrue(json.contains("\"model\":\"gpt-4o\""), "model 应输出");
        assertTrue(json.contains("\"max_tokens\":2048"), "max_tokens 应为 snake_case");
        assertTrue(json.contains("\"messages\":[{\"role\":\"system\""),
                "messages 应委托 ChatMessageSerializer 产出 OpenAI 形状: " + json);
    }

    @Test
    void tokenUsage_roundTripViaNewDeserializerInterface() {
        String json = "{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}";
        TokenUsage usage = YdszJson.toObject(json, TokenUsage.class);

        assertEquals(10, usage.getPromptTokens());
        assertEquals(20, usage.getCompletionTokens());
        assertEquals(30, usage.getTotalTokens());
    }

    @Test
    void toolCall_roundTripViaNewDeserializerInterface() {
        String json = "{\"id\":\"call-1\",\"type\":\"function\","
                + "\"function\":{\"name\":\"getWeather\","
                + "\"arguments\":\"{\\\"city\\\":\\\"Beijing\\\"}\"}}";
        ToolCall tc = YdszJson.toObject(json, ToolCall.class);

        assertEquals("call-1", tc.getId());
        assertEquals("getWeather", tc.getName());
        assertEquals("Beijing", tc.getArguments().get("city"));
    }
}
