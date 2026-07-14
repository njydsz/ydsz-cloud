package com.njydsz.pmis.common.json.engine;

import java.lang.reflect.Type;

import com.njydsz.pmis.common.json.provider.DeserializationProvider;
import com.njydsz.pmis.common.json.type.JsonType;

/**
 * Json 反序列化引擎（Facade 模式）
 *
 * <p>架构层级：Json => Engine => Provider => Parser</p>
 *
 * @since 1.3.0
 */
public final class DeserializerEngine {

    private DeserializerEngine() {
        throw new UnsupportedOperationException();
    }

    /**
     * 反序列化 JSON 字符串
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        return DeserializationProvider.deserialize(json, clazz);
    }

    /**
     * 反序列化 JSON 字符串（带特性配置）
     *
     * @param json JSON 字符串
     * @param clazz 目标类型
     * @param features 特性标志（位运算值）
     * @param <T> 类型参数
     * @return 反序列化后的对象
     */
    public static <T> T deserialize(String json, Class<T> clazz, long features) {
        return DeserializationProvider.deserialize(json, clazz, features);
    }

    /**
     * 反序列化 JSON 字符串（支持 Type）
     */
    
    public static <T> T deserialize(String json, Type type) {
        return DeserializationProvider.deserialize(json, type);
    }

    /**
     * 反序列化 JSON 字符串（支持 JsonType）
     */
    
    public static <T> T deserialize(String json, JsonType<T> typeRef) {
        return DeserializationProvider.deserialize(json, typeRef.getType());
    }

    /**
     * 反序列化器接口（ASM 使用）
     */
    public interface ObjectDeserializer {
        Object deserialize(String json);
    }
}
