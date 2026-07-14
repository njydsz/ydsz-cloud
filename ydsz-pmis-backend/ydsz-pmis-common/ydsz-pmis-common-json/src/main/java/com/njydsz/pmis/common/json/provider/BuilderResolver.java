package com.njydsz.pmis.common.json.provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.njydsz.pmis.common.json.annotation.JsonBuilder;
import com.njydsz.pmis.common.json.parser.JsonParser;

/**
 * @JsonBuilder 注解处理器
 *
 * <p>负责处理 Builder 模式的反序列化逻辑。</p>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
final class BuilderResolver {

    private static final Logger LOGGER = Logger.getLogger(BuilderResolver.class.getName());

    private BuilderResolver() {
        throw new UnsupportedOperationException();
    }

    static <T> T deserializeWithBuilder(String json, Class<T> clazz, JsonBuilder annotation) {
        Map<String, Object> map = JsonParser.parseObject(json);
        if (map == null || map.isEmpty()) {
            return CreatorResolver.createInstanceWithDefaultConstructor(clazz);
        }

        Class<?> builderClass = findBuilderClass(clazz, annotation);
        if (builderClass == null) {
            LOGGER.log(Level.WARNING, "Builder class not found for: {0}", clazz.getName());
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
                    LOGGER.log(Level.FINEST, "Error invoking setter: {0}", e.getMessage());
                }
            }

            String buildMethodName = annotation.buildMethod();
            Method buildMethod = builderClass.getMethod(buildMethodName);
            return clazz.cast(buildMethod.invoke(builderInstance));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Builder deserialization failed for {0}: {1}", new Object[]{clazz.getName(), e.getMessage()});
            return clazz.cast(map);
        }
    }

    static <T> T deserializeWithInnerBuilder(String json, Class<T> clazz, Class<?> builderClass, JsonBuilder annotation) {
        Map<String, Object> map = JsonParser.parseObject(json);
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
        }
        return clazz.cast(map);
    }

    static Class<?> findBuilderClass(Class<?> targetClass, JsonBuilder annotation) {
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

    static Class<?> findInnerBuilderClass(Class<?> targetClass) {
        for (Class<?> innerClass : targetClass.getDeclaredClasses()) {
            JsonBuilder annotation = innerClass.getAnnotation(JsonBuilder.class);
            if (annotation != null) {
                return innerClass;
            }
            if (isBuilderClass(innerClass, targetClass)) {
                return innerClass;
            }
        }
        return null;
    }

    static boolean isBuilderClass(Class<?> innerClass, Class<?> targetClass) {
        for (Method method : innerClass.getDeclaredMethods()) {
            if (method.getName().equals("build") && method.getReturnType().equals(targetClass)) {
                return true;
            }
        }
        return false;
    }

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

    static JsonBuilder createDefaultBuilderAnnotation() {
        return new JsonBuilder() {
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
            public Class<? extends Annotation> annotationType() { return JsonBuilder.class; }
        };
    }

    static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
}
