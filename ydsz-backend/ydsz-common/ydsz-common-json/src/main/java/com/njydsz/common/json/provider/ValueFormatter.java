package com.njydsz.common.json.provider;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import com.njydsz.common.json.annotation.JsonView;
import com.njydsz.common.json.cache.FieldMeta;
import com.njydsz.common.json.cache.SerializerCache;

/**
 * Pretty-print 格式化写入器
 *
 * <p>从 SerializationProvider 中提取的格式化序列化逻辑。</p>
 *
 * <p>提供带缩进的 JSON 格式化输出，调用 ValueWriter 进行值写入。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class ValueFormatter {

    private ValueFormatter() {
        throw new UnsupportedOperationException();
    }

    /**
     * 格式化写入值（带缩进）
     */
    public static void formatValue(Object obj, StringBuilder sb, int indent) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        Class<?> clazz = obj.getClass();

        if (clazz == String.class) {
            ValueWriter.writeString((String) obj, sb);
        } else if (obj instanceof List) {
            formatList((List<?>) obj, sb, indent);
        } else if (clazz.isArray()) {
            formatArray(obj, sb, indent);
        } else if (obj instanceof Map) {
            formatMap((Map<?, ?>) obj, sb, indent);
        } else if (clazz == Integer.class || clazz == Long.class || clazz == Double.class ||
                   clazz == Float.class || clazz == Boolean.class || clazz == Character.class ||
                   clazz == Short.class || clazz == Byte.class ||
                   clazz == BigDecimal.class || clazz == BigInteger.class) {
            sb.append(obj);
        } else {
            formatBean(obj, sb, indent);
        }
    }

    /**
     * 格式化 List
     */
    public static void formatList(List<?> list, StringBuilder sb, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            appendIndent(sb, indent + 1);
            formatValue(list.get(i), sb, indent + 1);
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        appendIndent(sb, indent);
        sb.append("]");
    }

    /**
     * 格式化数组
     */
    public static void formatArray(Object array, StringBuilder sb, int indent) {
        int len = Array.getLength(array);
        if (len == 0) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < len; i++) {
            appendIndent(sb, indent + 1);
            formatValue(Array.get(array, i), sb, indent + 1);
            if (i < len - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        appendIndent(sb, indent);
        sb.append("]");
    }

    /**
     * 格式化 Map
     */
    public static void formatMap(Map<?, ?> map, StringBuilder sb, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            appendIndent(sb, indent + 1);
            sb.append("\"").append(entry.getKey()).append("\"");
            sb.append(": ");
            formatValue(entry.getValue(), sb, indent + 1);
        }
        sb.append("\n");
        appendIndent(sb, indent);
        sb.append("}");
    }

    /**
     * 格式化 Bean
     */
    public static void formatBean(Object obj, StringBuilder sb, int indent) {
        Class<?> clazz = obj.getClass();
        FieldMeta[] fields = SerializerCache.getFieldMeta(clazz);
        if (fields == null) {
            fields = FieldMetadataLoader.loadFields(clazz);
            SerializerCache.putFieldMeta(clazz, fields);
        }

        if (fields.length == 0) {
            sb.append("{}");
            return;
        }

        Class<?> currentView = SerializationProvider.getCurrentViewClass();
        if (currentView != null) {
            List<FieldMeta> filteredFields = new ArrayList<>(fields.length);
            for (FieldMeta field : fields) {
                JsonView viewAnnotation = field.field.getAnnotation(JsonView.class);
                if (viewAnnotation == null) {
                    continue;
                }
                Class<?>[] viewClasses = viewAnnotation.value();
                boolean visible = false;
                for (Class<?> vc : viewClasses) {
                    if (vc == currentView || vc.isAssignableFrom(currentView)) {
                        visible = true;
                        break;
                    }
                }
                if (visible) {
                    filteredFields.add(field);
                }
            }
            if (filteredFields.isEmpty()) {
                sb.append("{}");
                return;
            }
            fields = filteredFields.toArray(new FieldMeta[0]);
        }

        sb.append("{\n");
        boolean first = true;
        for (FieldMeta field : fields) {
            try {
                Object value = field.getValue(obj);
                if (!first) sb.append(",\n");
                first = false;
                appendIndent(sb, indent + 1);
                sb.append("\"").append(field.jsonName).append("\": ");
                formatValue(value, sb, indent + 1);
            } catch (Exception e) {
                // 反射操作失败，忽略此路径，回退到默认行为
            }
        }
        sb.append("\n");
        appendIndent(sb, indent);
        sb.append("}");
    }

    /**
     * 追加缩进
     */
    public static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent * 2; i++) {
            sb.append(' ');
        }
    }
}
