package com.remisoft.common.json.deserializer;

import com.remisoft.common.json.reader.JSONReader;

/**
 * 自定义反序列化器接口
 *
 * <p>允许用户注册自定义反序列化逻辑。</p>
 *
 * <p>使用示例：</p>
 * <pre>
 * public class CustomUserDeserializer implements JsonDeserializer&lt;User&gt; {
 *     public User deserialize(JSONDeserializer in) {
 *         in.beginObject();
 *         Long id = in.readNumber("id", Long.class);
 *         String name = in.readString("name");
 *         in.endObject();
 *         return new User(id, name);
 *     }
 * }
 * </pre>
 *
 * @param <T> 反序列化的目标类型
 * @author remi-team
 * @since 1.0.0
 */
public interface JsonDeserializer<T> {

    /**
     * 反序列化对象
     *
     * @param in 输入反序列化器（用于读取 JSON）
     * @return 反序列化后的对象
     */
    T deserialize(JSONReader in);
}
