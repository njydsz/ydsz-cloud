package com.njydsz.agent.domain.json;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.TokenUsage;
import com.njydsz.agent.domain.model.ToolCall;
import com.njydsz.agent.domain.model.ToolDefinition;
import com.njydsz.common.json.module.JsonModule;
import com.njydsz.common.json.module.ModuleDeserializerRegistry;
import com.njydsz.common.json.module.ModuleSerializerRegistry;

/**
 * Agent 模块 YdszJson SPI 注册。
 *
 * <p>通过 {@link JsonModule.SpringFactory} 机制将 Agent 领域模型
 * （{@link ChatRequest} / {@link ChatMessage} / {@link ToolCall} / {@link ToolDefinition} / {@link TokenUsage}）
 * 的自定义序列化器（与部分反序列化器）注册到 YdszJson 引擎，使 LLM API 的 JSON 形状
 * （snake_case、role 用 API 枚举值、tool_calls 结构、arguments 为 JSON 字符串）在全局
 * {@code toJson/toObject} 路径中统一产出，替代 {@code OpenAiCompatibleClient} 中手工拼装请求体的冗余代码。</p>
 *
 * <p><b>本模块同时验证 P1-1 的核心修复：</b>{@code JsonModule} 注册中心此前在序列化/反序列化的
 * 真实路径中从未被查询（形同死代码），现已在 {@code SerializationProvider} / {@code DeserializationProvider}
 * 接入 —— {@code JsonModule.SpringFactory} 注册的序列化器/反序列化器可真正在全局路径中生效。
 * 由 {@code JsonAutoConfiguration.JsonConfigBean} 自动发现并注册（与 {@code SafeJsonModule} 同源机制）。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AgentJsonModule implements JsonModule, JsonModule.SpringFactory {

    @Override
    public String getModuleName() {
        return "agentJsonModule";
    }

    @Override
    public void setSerializers(ModuleSerializerRegistry registry) {
        registry.register(ChatRequest.class, new ChatRequestSerializer());
        registry.register(ChatMessage.class, new ChatMessageSerializer());
        registry.register(ToolCall.class, new ToolCallSerializer());
        registry.register(ToolDefinition.class, new ToolDefinitionSerializer());
        registry.register(TokenUsage.class, new TokenUsageSerializer());
    }

    @Override
    public void setDeserializers(ModuleDeserializerRegistry registry) {
        registry.register(TokenUsage.class, new TokenUsageDeserializer());
        registry.register(ToolCall.class, new ToolCallDeserializer());
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
