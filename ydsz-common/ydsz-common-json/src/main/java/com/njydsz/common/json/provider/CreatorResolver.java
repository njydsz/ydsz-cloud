package com.njydsz.common.json.provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import com.njydsz.common.json.annotation.JsonCreator;
import com.njydsz.common.json.annotation.JsonProperty;
import com.njydsz.common.json.parser.JsonParserUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link JsonCreator} 注解处理器。
 *
 * <p>负责处理带 {@code @JsonCreator} 注解的构造函数反序列化逻辑。
 * 当目标类没有默认无参构造函数时，通过注解标记的构造函数进行反序列化，
 * 类似 Jackson 的 {@code @JsonCreator} 和 Gson 的 {@code @SerializedName} 机制。
 *
 * <h3>参数名解析策略</h3>
 * <ol>
 *   <li>优先使用 {@code @JsonCreator(parameterNames=...)} 显式指定的参数名数组</li>
 *   <li>降级为通过类字段名 + {@code @JsonProperty} 注解映射 JSON 字段名</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JsonCreator
 * @see BuilderResolver
 * @see TypeConverter
 */
@SuppressWarnings("deprecation")
final class CreatorResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreatorResolver.class);

    private CreatorResolver() {
        throw new UnsupportedOperationException();
    }

    /**
     * 查找类上带 {@code @JsonCreator} 注解的构造函数。
     *
     * <p>如果多个构造函数都标注了该注解，优先选择 {@code defaultCreator=true} 的构造函数。
     * 其次选择最后遍历到的已标注构造函数。
     *
     * @param clazz 目标类
     * @return 标注了 {@code @JsonCreator} 且 {@code enable=true} 的构造函数，未找到返回 {@code null}
     */
    static Constructor<?> findCreatorConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        Constructor<?> annotatedConstructor = null;

        for (Constructor<?> ctor : constructors) {
            JsonCreator annotation = ctor.getAnnotation(JsonCreator.class);
            if (annotation != null && annotation.enable()) {
                if (annotation.defaultCreator() || annotatedConstructor == null) {
                    annotatedConstructor = ctor;
                }
            }
        }

        return annotatedConstructor;
    }

    /**
     * 使用带 {@code @JsonCreator} 的构造函数进行反序列化。
     *
     * <p>解析流程：
     * <ol>
     *   <li>将 JSON 解析为 Map</li>
     *   <li>如果注解指定了 {@code parameterNames}，按名称从 Map 中取值并转换类型后传参</li>
     *   <li>否则降级为字段反射赋值 + Setter 方式创建实例</li>
     *   <li>通过反射调用构造函数创建实例</li>
     * </ol>
     *
     * @param json        JSON 字符串
     * @param constructor 带注解的构造函数
     * @return 反序列化后的实例，创建失败返回 {@code null}
     */
    static Object deserializeWithCreator(String json, Constructor<?> constructor) {
        Map<String, Object> map = JsonParserUtil.parseObject(json);
        if (map == null || map.isEmpty()) {
            try {
                return constructor.newInstance();
            } catch (Exception e) {
                LOGGER.warn("Constructor invocation failed for {} (empty map)", constructor.getDeclaringClass().getName(), e);
                return null;
            }
        }

        JsonCreator annotation = constructor.getAnnotation(JsonCreator.class);
        String[] parameterNames = annotation != null && annotation.parameterNames().length > 0
                ? annotation.parameterNames() : null;

        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        if (parameterNames != null && parameterNames.length == paramTypes.length) {
            for (int i = 0; i < paramTypes.length; i++) {
                String jsonFieldName = parameterNames[i];
                Object value = map.get(jsonFieldName);
                args[i] = TypeConverter.convertValue(value, paramTypes[i]);
            }
        } else {
            // 未指定 parameterNames 或长度不匹配，降级为无参构造 + 字段反射赋值
            return createInstanceWithSetters(map, constructor.getDeclaringClass());
        }

        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (Exception e) {
            LOGGER.warn("Constructor invocation failed for {}", constructor.getDeclaringClass().getName(), e);
            return null;
        }
    }

    /**
     * 通过无参构造函数创建实例，再通过字段反射逐个赋值。
     *
     * <p>字段名映射优先级：{@code @JsonProperty(value)} > Java 字段名。
     * 值通过 {@link TypeConverter#convertValue} 进行类型转换。
     *
     * @param map   JSON 解析后的字段 Map
     * @param clazz 目标类
     * @param <T>   目标类型
     * @return 反序列化后的实例，创建失败返回 {@code null}
     */
    static <T> T createInstanceWithSetters(Map<String, Object> map, Class<T> clazz) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName();
                JsonProperty fieldAnnotation = field.getAnnotation(JsonProperty.class);
                if (fieldAnnotation != null && !fieldAnnotation.value().isEmpty()) {
                    fieldName = fieldAnnotation.value();
                }

                Object value = map.get(fieldName);
                if (value != null) {
                    try {
                        field.setAccessible(true);
                        Object convertedValue = TypeConverter.convertValue(value, field.getType());
                        field.set(instance, convertedValue);
                    } catch (Exception e) {
                        LOGGER.warn("Field setter failed for {}.{}, skipping field", clazz.getName(), field.getName(), e);
                    }
                }
            }
            return instance;
        } catch (Exception e) {
            LOGGER.warn("Failed to create instance with setters for {}", clazz.getName(), e);
            return null;
        }
    }

    /**
     * 通过默认无参构造函数创建实例。
     *
     * <p>用于 JSON 为空或降级场景。构造函数通过反射调用，包含 {@code setAccessible(true)}。
     *
     * @param clazz 目标类
     * @param <T>   目标类型
     * @return 新实例，创建失败返回 {@code null}
     */
    static <T> T createInstanceWithDefaultConstructor(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            LOGGER.warn("Failed to create instance with default constructor for {}", clazz.getName(), e);
            return null;
        }
    }
}
