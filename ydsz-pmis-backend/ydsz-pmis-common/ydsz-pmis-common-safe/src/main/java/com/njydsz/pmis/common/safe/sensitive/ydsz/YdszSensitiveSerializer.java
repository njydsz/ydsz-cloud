package com.njydsz.pmis.common.safe.sensitive.remi;

import com.njydsz.pmis.common.safe.sensitive.SensitiveDataProcessor;
import com.njydsz.pmis.common.util.json.JsonUtils;

/**
 * RemiJson 脱敏序列化器
 *
 * <p>使用 Jackson 进行序列化前，先对敏感数据进行脱敏处理。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 手动序列化
 * UserDTO user = new UserDTO();
 * user.setPhone("13800138000");
 * String json = YdszSensitiveSerializer.serialize(user);
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 4.0.0
 * @see SensitiveDataProcessor
 */
public final class YdszSensitiveSerializer {

    private YdszSensitiveSerializer() {
    }

    /**
     * 序列化对象（脱敏处理）
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
     * 序列化对象（脱敏处理，带格式化）
     *
     * @param obj    待序列化的对象
     * @param pretty 是否格式化
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
     * 序列化对象（脱敏处理）
     *
     * @param obj   待序列化的对象
     * @param clazz 目标类型
     * @param <T>   类型参数
     * @return JSON 字符串
     */
    public static <T> String serialize(T obj, Class<T> clazz) {
        if (obj == null) {
            return "null";
        }
        Object desensitized = SensitiveDataProcessor.process(obj);
        return JsonUtils.toJson(desensitized);
    }

    /**
     * 反序列化（不进行脱敏，仅解析）
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
