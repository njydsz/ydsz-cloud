package com.njydsz.pmis.common.json.engine;

import com.njydsz.pmis.common.json.provider.YdszDeserializationProvider;
import com.njydsz.pmis.common.json.type.YdszJsonType;

import java.lang.reflect.Type;

/**
 * YdszJson 反序列化引擎（Facade 模式）
 *
 * <p>架构层级：YdszJson => Engine => Provider => Parser</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class YdszDeserializerEngine {

    private YdszDeserializerEngine() {
        throw new UnsupportedOperationException();
    }

    /**
     * 反序列化 JSON 字符串
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        return YdszDeserializationProvider.deserialize(json, clazz);
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
        return YdszDeserializationProvider.deserialize(json, clazz, features);
    }

    /**
     * 反序列化 JSON 字符串（支持 Type）
     */
    
    public static <T> T deserialize(String json, Type type) {
        return YdszDeserializationProvider.deserialize(json, type);
    }

    /**
     * 反序列化 JSON 字符串（支持 YdszJsonType）
     */
    
    public static <T> T deserialize(String json, YdszJsonType<T> typeRef) {
        return YdszDeserializationProvider.deserialize(json, typeRef.getType());
    }

    /**
     * 反序列化器接口（ASM 使用）
     */
    public interface ObjectDeserializer {
        Object deserialize(String json);
    }
}
