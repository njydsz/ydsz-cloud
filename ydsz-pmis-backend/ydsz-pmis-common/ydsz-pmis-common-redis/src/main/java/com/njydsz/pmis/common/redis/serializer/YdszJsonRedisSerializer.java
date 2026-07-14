package com.njydsz.pmis.common.redis.serializer;

import com.njydsz.pmis.common.json.YdszJson;
import org.springframework.data.redis.serializer.RedisSerializer;

public class YdszJsonRedisSerializer<T> implements RedisSerializer<T> {
    private final Class<T> type;
    public YdszJsonRedisSerializer(Class<T> type) { this.type = type; }
    @Override public byte[] serialize(T t) { return t == null ? null : YdszJson.toJson(t).getBytes(); }
    @Override public T deserialize(byte[] bytes) { return bytes == null || bytes.length == 0 ? null : YdszJson.toObject(new String(bytes), type); }
}