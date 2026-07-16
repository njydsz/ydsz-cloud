package com.njydsz.pmis.common.json.serializer;

import com.njydsz.pmis.common.json.writer.JSONWriter;

/**
 * 自定义序列化器接口
 *
 * <p>允许用户注册自定义序列化逻辑，扩展性对标 Jackson Module 和 Gson TypeAdapter。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * public class CustomUserSerializer implements JsonSerializer&lt;User&gt; {
 *     public void serialize(User user, JSONSerializer out) {
 *         out.writeStartObject();
 *         out.writeName("custom_id");
 *         out.writeNumber(user.getId());
 *         out.writeName("custom_name");
 *         out.writeString(user.getName().toUpperCase());
 *         out.writeEndObject();
 *     }
 * }
 * </pre>
 *
 * @param <T> 要序列化的类型
 * @since 1.0.0
 */
public interface JsonSerializer<T> {

    /**
     * 序列化对象
     *
     * @param object 要序列化的对象
     * @param out 输出序列化器（用于写入 JSON）
     */
    void serialize(T object, JSONWriter out);

    /**
     * 是否支持指定类型
     *
     * @param type 类型
     * @return 如果支持返回 true
     */
    default boolean supports(Class<?> type) {
        return true;
    }
}
