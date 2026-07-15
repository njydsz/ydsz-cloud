package com.njydsz.pmis.common.redis.serializer;

import java.nio.charset.StandardCharsets;

import com.njydsz.pmis.common.json.Json;
import org.springframework.data.redis.serializer.RedisSerializer;

public class JsonRedisSerializer<T> implements RedisSerializer<T> {
    private final Class<T> type;
    public JsonRedisSerializer(Class<T> type) { this.type = type; }
    @Override public byte[] serialize(T t) { return t == null ? null : Json.toJson(t).getBytes(StandardCharsets.UTF_8); }
    @Override public T deserialize(byte[] bytes) { return bytes == null || bytes.length == 0 ? null : Json.toObject(new String(bytes, StandardCharsets.UTF_8), type); }
}