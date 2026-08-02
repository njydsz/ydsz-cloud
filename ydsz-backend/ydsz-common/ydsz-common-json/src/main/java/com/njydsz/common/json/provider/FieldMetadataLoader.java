package com.njydsz.common.json.provider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

import com.njydsz.common.json.annotation.JsonAutoDetect;
import com.njydsz.common.json.annotation.YdszJsonClass;
import com.njydsz.common.json.annotation.YdszJsonField;
import com.njydsz.common.json.annotation.JsonGetter;
import com.njydsz.common.json.annotation.JsonIgnore;
import com.njydsz.common.json.annotation.JsonIgnoreProperties;
import com.njydsz.common.json.annotation.JsonProperty;
import com.njydsz.common.json.annotation.JsonSetter;
import com.njydsz.common.json.annotation.YdszJsonPropertyOrder;
import com.njydsz.common.json.annotation.YdszJsonVisibility;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.naming.PropertyNamingStrategy;

/**
 * 字段元数据加载器和注解处理器
 *
 * <p>从 SerializationProvider 中提取的字段元数据加载逻辑。</p>
 *
 * <p>负责：</p>
 * <ul>
 *   <li>加载类的字段元数据（loadFields）</li>
 *   <li>检测字段注解（hasFieldAnnotations）</li>
 *   <li>判断字段可见性（isFieldVisible）</li>
 *   <li>扫描 @JsonGetter/@JsonSetter 方法级注解，覆盖字段 JSON 名称映射</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class FieldMetadataLoader {

    /** 当前使用的命名策略 */
    static final ThreadLocal<PropertyNamingStrategy> NAMING_STRATEGY =
        ThreadLocal.withInitial(() -> PropertyNamingStrategy.LOWER_CAMEL_CASE);

    private FieldMetadataLoader() {
        throw new UnsupportedOperationException();
    }

    /**
     * 加载字段元数据
     */
    public static FieldMeta[] loadFields(Class<?> clazz) {
        YdszJsonClass classAnnotation = clazz.getAnnotation(YdszJsonClass.class);

        int annotationFieldCount = classAnnotation != null
            ? classAnnotation.ignores().length + classAnnotation.includes().length + classAnnotation.ordering().length
            : 0;

        Set<String> ignores = new HashSet<>(annotationFieldCount);
        Set<String> includes = null;
        Map<String, Integer> ordering = new HashMap<>(annotationFieldCount);
        PropertyNamingStrategy classNaming = NAMING_STRATEGY.get();

        if (classAnnotation != null) {
            if (classAnnotation.ignores().length > 0) {
                ignores.addAll(Arrays.asList(classAnnotation.ignores()));
            }
            if (classAnnotation.includes().length > 0) {
                includes = new HashSet<>(annotationFieldCount);
                includes.addAll(Arrays.asList(classAnnotation.includes()));
            }
            if (classAnnotation.ordering().length > 0) {
                for (int i = 0; i < classAnnotation.ordering().length; i++) {
                    ordering.put(classAnnotation.ordering()[i], i);
                }
            }
            if (classAnnotation.naming() != YdszJsonClass.NamingStrategy.CAMEL_CASE) {
                switch (classAnnotation.naming()) {
                    case SNAKE_CASE:
                        classNaming = PropertyNamingStrategy.SNAKE_CASE;
                        break;
                    case KEBAB_CASE:
                        classNaming = PropertyNamingStrategy.KEBAB_CASE;
                        break;
                    case CAMEL_CASE:
                        classNaming = PropertyNamingStrategy.LOWER_CAMEL_CASE;
                        break;
                    case ORIGINAL:
                        classNaming = null;
                        break;
                }
            }
        }

        YdszJsonPropertyOrder propertyOrder = clazz.getAnnotation(YdszJsonPropertyOrder.class);
        Map<String, Integer> propertyOrderMapping = new HashMap<>();
        boolean alphabeticSort = false;
        if (propertyOrder != null) {
            if (propertyOrder.value().length > 0) {
                for (int i = 0; i < propertyOrder.value().length; i++) {
                    propertyOrderMapping.put(propertyOrder.value()[i], i);
                }
            }
            alphabeticSort = propertyOrder.alphabetic();
        }

        // 处理 @JsonIgnoreProperties 注解
        JsonIgnoreProperties ignoreProperties = clazz.getAnnotation(JsonIgnoreProperties.class);
        if (ignoreProperties != null) {
            for (String name : ignoreProperties.value()) {
                ignores.add(name);
            }
        }

        YdszJsonVisibility visibilityAnnotation = clazz.getAnnotation(YdszJsonVisibility.class);
        YdszJsonVisibility.Visibility fieldVisibility = YdszJsonVisibility.Visibility.ANY;
        if (visibilityAnnotation != null) {
            fieldVisibility = visibilityAnnotation.fields();
        }

        // Jackson 兼容：@JsonAutoDetect 映射到 YdszJsonVisibility
        JsonAutoDetect autoDetect = clazz.getAnnotation(JsonAutoDetect.class);
        if (autoDetect != null && autoDetect.fieldVisibility() != JsonAutoDetect.Visibility.DEFAULT) {
            fieldVisibility = autoDetect.fieldVisibility().toYdszVisibility();
        }

        Field[] declaredFields = clazz.getDeclaredFields();
        List<FieldMeta> fieldList = new ArrayList<>(declaredFields.length);

        for (Field field : declaredFields) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                continue;
            }

            if (!isFieldVisible(mods, fieldVisibility, field)) {
                continue;
            }

            String fieldName = field.getName();

            if (ignores.contains(fieldName)) {
                continue;
            }

            YdszJsonField jsonField = field.getAnnotation(YdszJsonField.class);
            JsonProperty jacksonProperty = field.getAnnotation(JsonProperty.class);
            JsonIgnore jacksonIgnore = field.getAnnotation(JsonIgnore.class);
            if (jsonField != null && jsonField.ignore()) {
                continue;
            }
            if (jacksonIgnore != null) {
                continue;
            }

            if (includes != null) {
                String jsonFieldName = field.getName();
                if (jsonField != null) {
                    if (!jsonField.value().isEmpty()) {
                        jsonFieldName = jsonField.value();
                    } else if (!jsonField.name().isEmpty()) {
                        jsonFieldName = jsonField.name();
                    } else if (classNaming != null) {
                        jsonFieldName = classNaming.translate(field.getName());
                    }
                } else if (classNaming != null) {
                    jsonFieldName = classNaming.translate(field.getName());
                }
                if (!includes.contains(jsonFieldName)) {
                    continue;
                }
            }

            String jsonName = fieldName;
            int ordinal = ordering.getOrDefault(fieldName, fieldList.size());

            if (propertyOrderMapping.containsKey(fieldName)) {
                ordinal = propertyOrderMapping.get(fieldName);
            }

            if (jsonField != null) {
                if (!jsonField.value().isEmpty()) {
                    jsonName = jsonField.value();
                } else if (!jsonField.name().isEmpty()) {
                    jsonName = jsonField.name();
                } else if (jacksonProperty != null && !jacksonProperty.value().isEmpty()) {
                    jsonName = jacksonProperty.value();
                } else if (classNaming != null) {
                    jsonName = classNaming.translate(jsonName);
                }

                if (jsonField.ordinal() != 0) {
                    ordinal = jsonField.ordinal();
                }
            } else if (jacksonProperty != null && !jacksonProperty.value().isEmpty()) {
                jsonName = jacksonProperty.value();
            } else if (classNaming != null) {
                jsonName = classNaming.translate(jsonName);
            }

            try {
                field.setAccessible(true);
                fieldList.add(new FieldMeta(field, jsonName, ordinal, jsonField));
            } catch (Exception e) {
                // 反射操作失败，忽略此路径，回退到默认行为
            }
        }

        if (alphabeticSort && propertyOrderMapping.isEmpty()) {
            fieldList.sort((a, b) -> a.jsonName.compareTo(b.jsonName));
        } else {
            fieldList.sort((a, b) -> Integer.compare(a.ordinal, b.ordinal));
        }

        // 扫描 @JsonGetter/@JsonSetter 方法级注解，覆盖字段 JSON 名称映射
        applyMethodAnnotations(clazz, fieldList, classNaming);

        return fieldList.toArray(new FieldMeta[0]);
    }

    /**
     * 扫描 @JsonGetter/@JsonSetter 方法级注解，覆盖字段 JSON 名称映射。
     *
     * <p>当 getter/setter 方法上标注了 @JsonGetter/@JsonSetter 且指定了 value 时，
     * 将对应字段的 JSON 名称覆盖为注解指定的值。如果方法没有对应的字段
     * （计算属性），当前版本跳过并记录 debug 日志。</p>
     *
     * @param clazz 被扫描的类
     * @param fieldList 已加载的字段列表
     * @param classNaming 类级命名策略
     * @since 1.4.0
     */
    private static void applyMethodAnnotations(Class<?> clazz, List<FieldMeta> fieldList, PropertyNamingStrategy classNaming) {
        // 构建字段名 -> 索引映射（用于替换 fieldList 中的元素）
        Map<String, Integer> fieldIndex = new HashMap<>(fieldList.size());
        for (int i = 0; i < fieldList.size(); i++) {
            fieldIndex.put(fieldList.get(i).name, i);
        }

        for (Method method : clazz.getDeclaredMethods()) {
            // @JsonGetter：覆盖序列化 JSON 名称
            JsonGetter jsonGetter = method.getAnnotation(JsonGetter.class);
            if (jsonGetter != null) {
                String fieldName = inferFieldNameFromGetter(method.getName());
                if (fieldName != null && fieldIndex.containsKey(fieldName)) {
                    String newJsonName = jsonGetter.value().isEmpty() ? fieldName : jsonGetter.value();
                    int idx = fieldIndex.get(fieldName);
                    FieldMeta original = fieldList.get(idx);
                    // 创建新的 FieldMeta，覆盖 jsonName
                    FieldMeta replaced = createWithJsonName(original, newJsonName);
                    if (replaced != null) {
                        fieldList.set(idx, replaced);
                    }
                }
            }

            // @JsonSetter：覆盖反序列化 JSON 名称
            JsonSetter jsonSetter = method.getAnnotation(JsonSetter.class);
            if (jsonSetter != null) {
                String fieldName = inferFieldNameFromSetter(method.getName());
                if (fieldName != null && fieldIndex.containsKey(fieldName)) {
                    String newJsonName = jsonSetter.value().isEmpty() ? fieldName : jsonSetter.value();
                    int idx = fieldIndex.get(fieldName);
                    FieldMeta original = fieldList.get(idx);
                    // 如果 @JsonGetter 已经覆盖过，在此基础再覆盖
                    String currentName = original.jsonName;
                    if (!newJsonName.equals(currentName)) {
                        FieldMeta replaced = createWithJsonName(original, newJsonName);
                        if (replaced != null) {
                            fieldList.set(idx, replaced);
                        }
                    }
                }
            }
        }
    }

    /**
     * 创建一个新的 FieldMeta，使用指定的 jsonName 替代原始的 jsonName。
     *
     * <p>由于 {@link FieldMeta} 的 {@code jsonName} 是 final 字段，
     * 需要通过反射构造一个新的实例。如果创建失败返回 null（保持原始实例）。</p>
     *
     * @param original 原始 FieldMeta
     * @param newJsonName 新的 JSON 名称
     * @return 新的 FieldMeta 实例，或 null 如果创建失败
     */
    private static FieldMeta createWithJsonName(FieldMeta original, String newJsonName) {
        try {
            YdszJsonField annotation = original.field.getAnnotation(YdszJsonField.class);
            FieldMeta replaced = new FieldMeta(original.field, newJsonName, original.ordinal, annotation);
            return replaced;
        } catch (Exception e) {
            // 创建失败，保持原始 FieldMeta
            return null;
        }
    }

    /**
     * 从 getter 方法名推断字段名（如 getName → name, isActive → active）。
     *
     * @param methodName 方法名
     * @return 字段名，或 null 如果无法推断
     */
    private static String inferFieldNameFromGetter(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return null;
    }

    /**
     * 从 setter 方法名推断字段名（如 setName → name）。
     *
     * @param methodName 方法名
     * @return 字段名，或 null 如果无法推断
     */
    private static String inferFieldNameFromSetter(String methodName) {
        if (methodName.startsWith("set") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        }
        return null;
    }

    /**
     * 检查字段是否有注解
     */
    public static boolean hasFieldAnnotations(FieldMeta[] fields) {
        if (fields == null) return false;
        for (FieldMeta field : fields) {
            if (!field.numberFormat.isEmpty() ||
                field.htmlSafe ||
                field.writeNull ||
                field.hasCustomSerializer() ||
                field.isDateType()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断字段是否可见（基于可见性策略）
     *
     * @param modifiers 字段修饰符
     * @param visibility 可见性级别
     * @param field 字段对象
     * @return 是否可见
     */
    public static boolean isFieldVisible(int modifiers, YdszJsonVisibility.Visibility visibility, Field field) {
        switch (visibility) {
            case NONE:
                return false;
            case PUBLIC_ONLY:
                return Modifier.isPublic(modifiers);
            case PROTECTED_AND_PUBLIC:
                return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
            case ANY:
            default:
                return true;
        }
    }
}
