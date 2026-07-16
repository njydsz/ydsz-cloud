package com.njydsz.common.json.provider;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import com.njydsz.common.json.annotation.JsonCreator;
import com.njydsz.common.json.annotation.JsonField;
import com.njydsz.common.json.parser.JsonParser;

/**
 * @JsonCreator 注解处理器
 *
 * <p>负责处理带 @JsonCreator 注解的构造函数反序列化逻辑。</p>
 *
 * @since 1.0.0
 */
final class CreatorResolver {

    private CreatorResolver() {
        throw new UnsupportedOperationException();
    }

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

    static Object deserializeWithCreator(String json, Constructor<?> constructor) {
        Map<String, Object> map = JsonParser.parseObject(json);
        if (map == null || map.isEmpty()) {
            try {
                return constructor.newInstance();
            } catch (Exception e) {
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
            Field[] fields = constructor.getDeclaringClass().getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName();
                JsonField fieldAnnotation = field.getAnnotation(JsonField.class);
                if (fieldAnnotation != null && !fieldAnnotation.name().isEmpty()) {
                    fieldName = fieldAnnotation.name();
                } else if (fieldAnnotation != null && !fieldAnnotation.value().isEmpty()) {
                    fieldName = fieldAnnotation.value();
                }

                Object value = map.get(fieldName);
                if (value != null) {
                    try {
                        field.setAccessible(true);
                        Object convertedValue = TypeConverter.convertValue(value, field.getType());
                        field.set(null, convertedValue);
                    } catch (Exception e) {
                    }
                }
            }
            return createInstanceWithSetters(map, constructor.getDeclaringClass());
        }

        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (Exception e) {
            return null;
        }
    }

    static <T> T createInstanceWithSetters(Map<String, Object> map, Class<T> clazz) {
        try {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                String fieldName = field.getName();
                JsonField fieldAnnotation = field.getAnnotation(JsonField.class);
                if (fieldAnnotation != null && !fieldAnnotation.name().isEmpty()) {
                    fieldName = fieldAnnotation.name();
                } else if (fieldAnnotation != null && !fieldAnnotation.value().isEmpty()) {
                    fieldName = fieldAnnotation.value();
                }

                Object value = map.get(fieldName);
                if (value != null) {
                    try {
                        field.setAccessible(true);
                        Object convertedValue = TypeConverter.convertValue(value, field.getType());
                        field.set(null, convertedValue);
                    } catch (Exception e) {
                    }
                }
            }

            T instance = clazz.getDeclaredConstructor().newInstance();
            for (Field field : fields) {
                String fieldName = field.getName();
                JsonField fieldAnnotation = field.getAnnotation(JsonField.class);
                if (fieldAnnotation != null && !fieldAnnotation.name().isEmpty()) {
                    fieldName = fieldAnnotation.name();
                } else if (fieldAnnotation != null && !fieldAnnotation.value().isEmpty()) {
                    fieldName = fieldAnnotation.value();
                }

                Object value = map.get(fieldName);
                if (value != null) {
                    try {
                        field.setAccessible(true);
                        Object convertedValue = TypeConverter.convertValue(value, field.getType());
                        field.set(instance, convertedValue);
                    } catch (Exception e) {
                    }
                }
            }
            return instance;
        } catch (Exception e) {
            return null;
        }
    }

    static <T> T createInstanceWithDefaultConstructor(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            return null;
        }
    }
}
