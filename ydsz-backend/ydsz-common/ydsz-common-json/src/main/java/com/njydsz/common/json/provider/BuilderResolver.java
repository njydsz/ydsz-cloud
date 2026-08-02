package com.njydsz.common.json.provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.annotation.YdszJsonBuilder;
import com.njydsz.common.json.parser.YdszJsonParser;

/**
 * {@link YdszJsonBuilder} 注解处理器。
 *
 * <p>负责处理 Builder 设计模式的 JSON 反序列化逻辑。当目标类使用 Builder 模式构建时，
 * 本类通过反射定位 Builder 类及其 setter 方法（如 {@code withXxx()}），
 * 逐字段设置值后调用 {@code build()} 方法生成目标实例。
 *
 * <h3>支持的 Builder 模式</h3>
 * <ul>
 *   <li><b>外部 Builder</b>：通过 {@code @YdszJsonBuilder(builderClass=...)} 显式指定 Builder 类</li>
 *   <li><b>内部 Builder</b>：Builder 作为目标类的静态内部类，通过 {@code autoDetect=true} 自动发现</li>
 * </ul>
 *
 * <h3>方法命名约定</h3>
 * <p>Builder 的 setter 方法名通过 {@code withPrefix} 配置：
 * <ul>
 *   <li>{@code withPrefix="with"} → 方法名为 {@code withFieldName}（如 {@code withName()}）</li>
 *   <li>{@code withPrefix=""} → 方法名为 {@code fieldName}（如 {@code name()}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see YdszJsonBuilder
 * @see CreatorResolver
 * @see TypeConverter
 */
final class BuilderResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuilderResolver.class);

    private BuilderResolver() {
        throw new UnsupportedOperationException();
    }

    /**
     * 使用外部 Builder 类进行反序列化。
     *
     * <p>流程：解析 JSON → 创建 Builder 实例 → 逐字段调用 setter → 调用 build() 方法。
     * 如果 Builder 类未找到或构建失败，降级为直接返回解析后的 Map。
     *
     * @param json       JSON 字符串
     * @param clazz      目标类
     * @param annotation {@code @YdszJsonBuilder} 注解配置
     * @param <T>        目标类型
     * @return 反序列化后的实例
     */
    static <T> T deserializeWithBuilder(String json, Class<T> clazz, YdszJsonBuilder annotation) {
        Map<String, Object> map = YdszJsonParser.parseObject(json);
        if (map == null || map.isEmpty()) {
            return CreatorResolver.createInstanceWithDefaultConstructor(clazz);
        }

        Class<?> builderClass = findBuilderClass(clazz, annotation);
        if (builderClass == null) {
            LOGGER.warn("Builder class not found for: {}", clazz.getName());
            return clazz.cast(map);
        }

        try {
            Object builderInstance;
            Constructor<?> builderConstructor = builderClass.getDeclaredConstructor();
            builderConstructor.setAccessible(true);
            builderInstance = builderConstructor.newInstance();

            String withPrefix = annotation.withPrefix();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();

                String methodName = withPrefix.isEmpty() ? fieldName : withPrefix + capitalize(fieldName);
                try {
                    Method setterMethod = findSetterMethod(builderClass, methodName, value);
                    if (setterMethod != null) {
                        setterMethod.setAccessible(true);
                        Object convertedValue = TypeConverter.convertValue(value, setterMethod.getParameterTypes()[0]);
                        setterMethod.invoke(builderInstance, convertedValue);
                    }
                } catch (Exception e) {
                    LOGGER.trace("Error invoking setter: {}", e.getMessage());
                }
            }

            String buildMethodName = annotation.buildMethod();
            Method buildMethod = builderClass.getMethod(buildMethodName);
            return clazz.cast(buildMethod.invoke(builderInstance));
        } catch (Exception e) {
            LOGGER.warn("Builder deserialization failed for {}: {}", clazz.getName(), e.getMessage());
            return clazz.cast(map);
        }
    }

    /**
     * 使用内部 Builder 类（静态内部类）进行反序列化。
     *
     * <p>与 {@link #deserializeWithBuilder} 类似，但 Builder 类是目标类的内部类。
     * 如果 build() 方法通过名称查找失败，会遍历所有方法查找返回类型匹配的无参方法。
     *
     * @param json         JSON 字符串
     * @param clazz        目标类
     * @param builderClass Builder 内部类
     * @param annotation   {@code @YdszJsonBuilder} 注解配置
     * @param <T>          目标类型
     * @return 反序列化后的实例
     */
    static <T> T deserializeWithInnerBuilder(String json, Class<T> clazz, Class<?> builderClass, YdszJsonBuilder annotation) {
        Map<String, Object> map = YdszJsonParser.parseObject(json);
        if (map == null || map.isEmpty()) {
            return CreatorResolver.createInstanceWithDefaultConstructor(clazz);
        }

        try {
            Constructor<?> builderConstructor = builderClass.getDeclaredConstructor();
            builderConstructor.setAccessible(true);
            Object builderInstance = builderConstructor.newInstance();

            String withPrefix = annotation.withPrefix();
            boolean usePrefix = !withPrefix.isEmpty();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String fieldName = entry.getKey();
                Object value = entry.getValue();

                String methodName = usePrefix ? withPrefix + capitalize(fieldName) : fieldName;
                try {
                    Method setterMethod = findSetterMethod(builderClass, methodName, value);
                    if (setterMethod != null) {
                        setterMethod.setAccessible(true);
                        Object convertedValue = TypeConverter.convertValue(value, setterMethod.getParameterTypes()[0]);
                        setterMethod.invoke(builderInstance, convertedValue);
                    }
                } catch (Exception e) {
                // 反射操作失败，忽略此路径，回退到默认行为
            }
            }

            String buildMethodName = annotation.buildMethod();
            try {
                Method buildMethod = builderClass.getMethod(buildMethodName);
                return clazz.cast(buildMethod.invoke(builderInstance));
            } catch (NoSuchMethodException e) {
                for (Method m : builderClass.getDeclaredMethods()) {
                    if (m.getName().equals(buildMethodName) && m.getParameterCount() == 0 && clazz.isAssignableFrom(m.getReturnType())) {
                        m.setAccessible(true);
                        return clazz.cast(m.invoke(builderInstance));
                    }
                }
            }
        } catch (Exception e) {
                // 反射操作失败，忽略此路径，回退到默认行为
            }
        return clazz.cast(map);
    }

    /**
     * 查找目标类的 Builder 类。
     *
     * <p>查找优先级：
     * <ol>
     *   <li>注解显式指定的 {@code builderClass()}</li>
     *   <li>{@code autoDetect=true} 时，遍历内部类查找含 {@code build()} 方法且返回目标类型的类</li>
     * </ol>
     *
     * @param targetClass 目标类
     * @param annotation  Builder 注解配置
     * @return Builder 类，未找到时返回 {@code null}
     */
    static Class<?> findBuilderClass(Class<?> targetClass, YdszJsonBuilder annotation) {
        if (annotation.builderClass() != void.class) {
            return annotation.builderClass();
        }

        if (annotation.autoDetect()) {
            for (Class<?> innerClass : targetClass.getDeclaredClasses()) {
                if (isBuilderClass(innerClass, targetClass)) {
                    return innerClass;
                }
            }
        }

        return null;
    }

    /**
     * 查找目标类的内部 Builder 类。
     *
     * <p>遍历目标类的所有内部类，优先查找带 {@code @YdszJsonBuilder} 注解的类，
     * 其次查找含 {@code build()} 方法且返回目标类型的类。
     *
     * @param targetClass 目标类
     * @return Builder 内部类，未找到时返回 {@code null}
     */
    static Class<?> findInnerBuilderClass(Class<?> targetClass) {
        for (Class<?> innerClass : targetClass.getDeclaredClasses()) {
            YdszJsonBuilder annotation = innerClass.getAnnotation(YdszJsonBuilder.class);
            if (annotation != null) {
                return innerClass;
            }
            if (isBuilderClass(innerClass, targetClass)) {
                return innerClass;
            }
        }
        return null;
    }

    /**
     * 判断一个内部类是否为 Builder 类。
     *
     * <p>判定标准：该类包含名为 {@code "build"} 且返回类型等于目标类的方法。
     *
     * @param innerClass  待判定的内部类
     * @param targetClass 目标类
     * @return 是 Builder 类返回 {@code true}
     */
    static boolean isBuilderClass(Class<?> innerClass, Class<?> targetClass) {
        for (Method method : innerClass.getDeclaredMethods()) {
            if (method.getName().equals("build") && method.getReturnType().equals(targetClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在 Builder 类中查找匹配的 setter 方法。
     *
     * <p>匹配规则：方法名相同 + 单参数 + 参数类型可赋值。
     * 对于 Number 类型，只要参数和值都是 Number 子类即视为匹配（支持 Integer→Long 等转换）。
     *
     * @param builderClass Builder 类
     * @param methodName   方法名（如 {@code withName}）
     * @param value        待设置的值（用于类型推断）
     * @return 匹配的 Method，未找到返回 {@code null}
     */
    static Method findSetterMethod(Class<?> builderClass, String methodName, Object value) {
        Class<?> valueClass = value != null ? value.getClass() : Object.class;

        for (Method method : builderClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                Class<?> paramType = method.getParameterTypes()[0];
                if (paramType.isAssignableFrom(valueClass)) {
                    return method;
                }
                if (Number.class.isAssignableFrom(valueClass) && Number.class.isAssignableFrom(paramType)) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * 创建默认的 {@link YdszJsonBuilder} 注解实例。
     *
     * <p>当内部 Builder 类未显式标注 {@code @YdszJsonBuilder} 时使用此默认配置：
     * buildMethod="build"、withPrefix=""、chainMethod=true。
     *
     * @return 默认注解实例
     */
    static YdszJsonBuilder createDefaultBuilderAnnotation() {
        return new YdszJsonBuilder() {
            @Override
            public Class<?> builderClass() { return void.class; }
            @Override
            public String buildMethod() { return "build"; }
            @Override
            public String withPrefix() { return ""; }
            @Override
            public boolean enable() { return true; }
            @Override
            public boolean autoDetect() { return false; }
            @Override
            public String builderConstructor() { return ""; }
            @Override
            public boolean chainMethod() { return true; }
            @Override
            public String[] ignoreMethods() { return new String[0]; }
            @Override
            public Class<? extends Annotation> annotationType() { return YdszJsonBuilder.class; }
        };
    }

    /**
     * 将字符串首字母大写，用于拼接 Builder setter 方法名。
     *
     * @param str 输入字符串
     * @return 首字母大写的字符串，null 或空字符串原样返回
     */
    static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
