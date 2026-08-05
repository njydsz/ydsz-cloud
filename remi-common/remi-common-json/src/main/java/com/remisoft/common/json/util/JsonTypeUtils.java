package com.remisoft.common.json.util;

/**
 * JSON 类型判断工具类（统一 isSimpleType / getTypeCode 等重复实现）。
 *
 * <p>此前 {@code BeanDeserializerEngine.isSimpleType()}、
 * {@code BeanReader} 等处独立实现了几乎相同的基本类型判断逻辑，此处统一为单一来源。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class JsonTypeUtils {

    private JsonTypeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断类型是否为基本类型或其包装类、String。
     *
     * <p>统一替代 BeanDeserializerEngine、BeanReader 等处的重复实现。</p>
     *
     * @param type 待判断的类型
     * @return 是基本类型返回 true
     */
    public static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               type == Integer.class || type == int.class ||
               type == Long.class || type == long.class ||
               type == Double.class || type == double.class ||
               type == Float.class || type == float.class ||
               type == Boolean.class || type == boolean.class ||
               type == Short.class || type == short.class ||
               type == Byte.class || type == byte.class ||
               type == Character.class || type == char.class;
    }

    /**
     * 获取类型码。
     *
     * <ul>
     *   <li>1: int/Integer</li>
     *   <li>2: long/Long</li>
     *   <li>3: double/Double</li>
     *   <li>4: float/Float</li>
     *   <li>5: boolean/Boolean</li>
     *   <li>6: String</li>
     *   <li>0: 其他</li>
     * </ul>
     *
     * @param type 目标类型
     * @return 类型码
     */
    public static int getTypeCode(Class<?> type) {
        if (type == int.class || type == Integer.class) return 1;
        if (type == long.class || type == Long.class) return 2;
        if (type == double.class || type == Double.class) return 3;
        if (type == float.class || type == Float.class) return 4;
        if (type == boolean.class || type == Boolean.class) return 5;
        if (type == String.class) return 6;
        return 0;
    }
}
