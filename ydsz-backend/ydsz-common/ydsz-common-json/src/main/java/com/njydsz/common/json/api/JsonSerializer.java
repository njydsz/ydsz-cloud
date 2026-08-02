package com.njydsz.common.json.api;

/**
 * 自定义 JSON 序列化器接口。
 *
 * <p>通过 {@code @JsonSerialize(using = ...)} 注解指定，
 * 在序列化目标类型时使用自定义逻辑替代默认序列化。</p>
 *
 * @param <T> 要序列化的类型
 * @deprecated 使用 {@link com.njydsz.common.json.serializer.JsonSerializer} 替代（JSONWriter 原生支持，性能更优）。
 *             此接口（String 返回版）将在后续版本删除。
 */
@Deprecated
public interface JsonSerializer<T> {

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 要序列化的对象
     * @return JSON 字符串
     */
    String serialize(T value);
}
