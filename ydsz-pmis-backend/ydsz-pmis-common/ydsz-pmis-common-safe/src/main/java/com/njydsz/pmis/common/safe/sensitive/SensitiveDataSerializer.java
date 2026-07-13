package com.njydsz.pmis.common.safe.sensitive;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.njydsz.pmis.common.util.json.JsonUtils;

/**
 * 敏感数据脱敏 Jackson 序列化器
 *
 * <p>基于 Jackson {@link JsonSerializer} 实现，在序列化 JSON 时自动对
 * 标注了 {@link SensitiveData} 注解的字段进行脱敏处理。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * public class UserVO {
 *     @SensitiveData(SensitiveType.PHONE)
 *     @JsonSerialize(using = SensitiveDataSerializer.class)
 *     private String phone;
 * }
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>仅对 String 类型字段生效</li>
 *   <li>如果字段值为 null，保持 null 不处理</li>
 *   <li>脱敏失败时保留原始值，不影响其他字段</li>
 *   <li>配合 {@link SensitiveDataProcessor} 使用可实现更全面的脱敏</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @see SensitiveData
 * @see SensitiveType
 * @see SensitiveUtil
 */
public class SensitiveDataSerializer extends JsonSerializer<Object> {

    /**
     * 单例实例
     */
    public static final SensitiveDataSerializer INSTANCE = new SensitiveDataSerializer();

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }

        // 如果是简单类型（String 等），直接序列化
        if (value instanceof String) {
            gen.writeString((String) value);
            return;
        }

        // 先脱敏处理，再序列化
        Object desensitized = SensitiveDataProcessor.process(value);
        gen.writeObject(desensitized);
    }

    /**
     * 序列化对象（自动脱敏）
     *
     * @param obj 待序列化的对象
     * @return JSON 字符串
     */
    public static String serialize(Object obj) {
        if (obj == null) {
            return "null";
        }
        Object desensitized = SensitiveDataProcessor.process(obj);
        return JsonUtils.toJson(desensitized);
    }

    /**
     * 序列化对象（自动脱敏，带格式化）
     *
     * @param obj    待序列化的对象
     * @param pretty 是否格式化输出
     * @return JSON 字符串
     */
    public static String serialize(Object obj, boolean pretty) {
        if (obj == null) {
            return "null";
        }
        Object desensitized = SensitiveDataProcessor.process(obj);
        if (pretty) {
            return JsonUtils.toPrettyJson(desensitized);
        }
        return JsonUtils.toJson(desensitized);
    }

    /**
     * 反序列化（不进行脱敏）
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return 对象
     */
    public static <T> T deserialize(String json, Class<T> clazz) {
        return JsonUtils.fromJson(json, clazz);
    }
}
