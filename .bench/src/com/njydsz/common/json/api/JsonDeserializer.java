package com.njydsz.common.json.api;

/**
 * 自定义 JSON 反序列化器接口。
 *
 * <p>通过 {@code @JsonDeserialize(using = ...)} 注解指定，
 * 在反序列化目标类型时使用自定义逻辑替代默认反序列化。</p>
 *
 * @param <T> 要反序列化的类型
 * @deprecated 使用 {@link com.njydsz.common.json.deserializer.JsonDeserializer} 替代（JSONReader 原生支持，流式解析性能更优）。
 *             此接口（String 入参版）将在后续版本删除。
 */
@Deprecated
public interface JsonDeserializer<T> {

    /**
     * 将 JSON 字符串反序列化为对象。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @return 反序列化后的对象
     */
    T deserialize(String json, Class<T> type);
}
