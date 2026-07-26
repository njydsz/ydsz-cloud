package com.njydsz.common.json.provider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import com.njydsz.common.json.annotation.YdszJsonClass;
import com.njydsz.common.json.annotation.YdszJsonField;
import com.njydsz.common.json.annotation.JsonIgnoreProperties;
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
            if (jsonField != null && jsonField.ignore()) {
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
                } else if (classNaming != null) {
                    jsonName = classNaming.translate(jsonName);
                }

                if (jsonField.ordinal() != 0) {
                    ordinal = jsonField.ordinal();
                }
            } else if (classNaming != null) {
                jsonName = classNaming.translate(jsonName);
            }

            try {
                field.setAccessible(true);
                fieldList.add(new FieldMeta(field, jsonName, ordinal, jsonField));
            } catch (Exception e) {
            }
        }

        if (alphabeticSort && propertyOrderMapping.isEmpty()) {
            fieldList.sort((a, b) -> a.jsonName.compareTo(b.jsonName));
        } else {
            fieldList.sort((a, b) -> Integer.compare(a.ordinal, b.ordinal));
        }
        return fieldList.toArray(new FieldMeta[0]);
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
