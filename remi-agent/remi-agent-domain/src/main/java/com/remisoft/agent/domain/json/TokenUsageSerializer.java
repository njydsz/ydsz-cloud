package com.remisoft.agent.domain.json;

import com.remisoft.common.json.serializer.JsonSerializer;
import com.remisoft.common.json.writer.JSONWriter;
import com.remisoft.agent.domain.model.TokenUsage;

/**
 * {@link TokenUsage} 的 RemiJson 自定义序列化器（JsonModule SPI 落地 + OpenAI usage 形状）。
 *
 * <p>对标 OpenAI Chat Completions 响应中的 {@code usage} 字段（snake_case）：
 * {@code {"prompt_tokens":..,"completion_tokens":..,"total_tokens":..}}。
 * 替代默认 Bean 序列化（camelCase 字段名），使 Token 计量的 JSON 形状与 LLM API 契约一致。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class TokenUsageSerializer implements JsonSerializer<TokenUsage> {

    @Override
    public void serialize(TokenUsage usage, JSONWriter out) {
        if (usage == null) {
            out.write("null");
            return;
        }
        out.write("{\"prompt_tokens\":");
        out.writeInt(usage.getPromptTokens());
        out.write(",\"completion_tokens\":");
        out.writeInt(usage.getCompletionTokens());
        out.write(",\"total_tokens\":");
        out.writeInt(usage.getTotalTokens());
        out.write("}");
    }
}
