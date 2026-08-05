package com.remisoft.agent.domain.json;

import java.util.Map;

import com.remisoft.common.json.RemiJson;
import com.remisoft.common.json.deserializer.JsonDeserializer;
import com.remisoft.common.json.reader.JSONReader;
import com.remisoft.agent.domain.model.TokenUsage;

/**
 * {@link TokenUsage} 的 RemiJson 自定义反序列化器（验证 P1-1 反序列化引擎修复）。
 *
 * <p>通过 {@link JSONReader#readRawValue()} 捕获完整对象 JSON 后委托 {@link RemiJson#toObject(String, Class)}，
 * 避免手写 token 级解析的脆弱性。构造 {@link TokenUsage} 时 {@code totalTokens} 由其构造函数自动求和。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class TokenUsageDeserializer implements JsonDeserializer<TokenUsage> {

    @Override
    public TokenUsage deserialize(JSONReader in) {
        String raw = in.readRawValue();
        Map<String, Object> m = RemiJson.toObject(raw, Map.class);
        int prompt = asInt(m.get("prompt_tokens"));
        int completion = asInt(m.get("completion_tokens"));
        return new TokenUsage(prompt, completion);
    }

    private static int asInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}
