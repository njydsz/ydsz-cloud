package com.njydsz.common.safe.sensitive;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.serializer.JsonSerializer;
import com.njydsz.common.json.writer.JSONWriter;

/**
 * 敏感数据脱敏序列化器（基于 YdszJson 引擎）
 *
 * <p>实现 YdszJson {@link JsonSerializer}，在序列化 JSON 时自动对
 * 标注了 {@link SensitiveData} 注解的字段进行脱敏处理。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * public class UserVO {
 *     @SensitiveData(SensitiveType.PHONE)
 *     private String phone;
 * }
 * }</pre>
 *
 * <p><b>注册方式：</b>通过 {@link com.njydsz.common.safe.xss.SafeJsonModule}（实现
 * {@link com.njydsz.common.json.module.JsonModule.SpringFactory}）自动注册到 YdszJson 引擎，
 * 禁止在业务代码中散落调用 {@code YdszJson.register()}。
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>仅对 String 类型字段生效</li>
 *   <li>如果字段值为 null，保持 null 不处理</li>
 *   <li>脱敏失败时保留原始值，不影响其他字段</li>
 *   <li>配合 {@link SensitiveDataProcessor} 使用可实现更全面的脱敏</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see SensitiveData
 * @see SensitiveType
 * @see SensitiveUtil
 */
public class SensitiveDataSerializer implements JsonSerializer<Object> {

    /**
     * 单例实例
     */
    public static final SensitiveDataSerializer INSTANCE = new SensitiveDataSerializer();

    @Override
    public void serialize(Object value, JSONWriter out) {
        if (value == null) {
            out.write("null");
            return;
        }

        // 先脱敏处理，再序列化
        Object desensitized = SensitiveDataProcessor.process(value);
        out.write(YdszJson.toJson(desensitized));
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
        return YdszJson.toJson(desensitized);
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
            return YdszJson.format(desensitized);
        }
        return YdszJson.toJson(desensitized);
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
        return YdszJson.toObject(json, clazz);
    }
}
