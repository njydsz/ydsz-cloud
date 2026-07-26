package com.njydsz.common.excel.csv;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.tabular.TabularRowMapper;
import com.njydsz.common.excel.support.cache.ReflectCache;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;

/**
 * 基于 {@code @ExcelProperty} 注解的默认行映射器。
 *
 * <p>解析顺序：按 {@link ExcelProperty#order()} 升序；order 相同时按字段声明顺序。
 * 标注了 {@link ExcelIgnore} 的字段会被跳过。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * public class User {
 *     &#64;ExcelProperty(value = "姓名", order = 1)
 *     private String name;
 *
 *     &#64;ExcelProperty(value = "年龄", order = 2)
 *     private Integer age;
 * }
 *
 * TabularRowMapper<User> mapper = new DefaultAnnotationRowMapper<>(User.class);
 * User u = mapper.toRow(new String[]{"张三", "25"});
 * }</pre>
 *
 * @param <T> 目标类型
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultAnnotationRowMapper<T> implements TabularRowMapper<T> {

    /** 缓存已解析的 Class → Mapper 实例，避免重复反射 */
    private static final Map<Class<?>, DefaultAnnotationRowMapper<?>> CACHE = new ConcurrentHashMap<>();

    private final Class<T> clazz;
    private final List<String> headers;
    private final List<Field> orderedFields;
    private final ASMFieldAccessor.FieldGetter[] getters;
    private final ASMFieldAccessor.FieldSetter[] setters;

    public DefaultAnnotationRowMapper(Class<T> clazz) {
        this.clazz = clazz;
        this.orderedFields = collectOrderedFields(clazz);
        this.headers = buildHeaders(orderedFields);
        this.getters = new ASMFieldAccessor.FieldGetter[orderedFields.size()];
        this.setters = new ASMFieldAccessor.FieldSetter[orderedFields.size()];
        for (int i = 0; i < orderedFields.size(); i++) {
            Field f = orderedFields.get(i);
            f.setAccessible(true);
            this.getters[i] = ASMFieldAccessor.getGetter(clazz, f);
            this.setters[i] = ASMFieldAccessor.getSetter(clazz, f);
        }
    }

    /**
     * 静态工厂方法（带缓存）。
     */
    @SuppressWarnings("unchecked")
    public static <T> DefaultAnnotationRowMapper<T> of(Class<T> clazz) {
        return (DefaultAnnotationRowMapper<T>) CACHE.computeIfAbsent(clazz, DefaultAnnotationRowMapper::new);
    }

    @Override
    public List<String> headers() {
        return headers;
    }

    @Override
    public T toRow(String[] values) {
        if (values == null) {
            return null;
        }
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            int n = Math.min(values.length, orderedFields.size());
            for (int i = 0; i < n; i++) {
                String v = values[i];
                if (v == null || v.isEmpty()) {
                    continue;
                }
                Field f = orderedFields.get(i);
                Object converted = convert(v, f.getType());
                setters[i].set(instance, converted);
            }
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to instantiate " + clazz.getName() + " from CSV row", e);
        }
    }

    @Override
    public String[] fromRow(T object) {
        if (object == null) {
            return new String[orderedFields.size()];
        }
        String[] values = new String[orderedFields.size()];
        for (int i = 0; i < orderedFields.size(); i++) {
            Object raw;
            try {
                raw = getters[i].get(object);
            } catch (Exception e) {
                raw = null;
            }
            values[i] = stringify(raw);
        }
        return values;
    }

    @Override
    public Optional<String> getValue(T object, String columnName) {
        if (object == null || columnName == null) {
            return Optional.empty();
        }
        for (int i = 0; i < headers.size(); i++) {
            if (columnName.equalsIgnoreCase(headers.get(i))) {
                try {
                    return Optional.ofNullable(stringify(getters[i].get(object)));
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    // ==================== 内部辅助 ====================

    private static List<Field> collectOrderedFields(Class<?> clazz) {
        Field[] all = ReflectCache.getCachedFields(clazz);
        List<Field> annotated = new ArrayList<>();
        for (Field f : all) {
            if (f.isAnnotationPresent(ExcelIgnore.class)) {
                continue;
            }
            if (f.isAnnotationPresent(ExcelProperty.class)) {
                annotated.add(f);
            }
        }
        annotated.sort(Comparator.comparingInt(f -> f.getAnnotation(ExcelProperty.class).order()));
        return annotated;
    }

    private static List<String> buildHeaders(List<Field> fields) {
        List<String> hs = new ArrayList<>(fields.size());
        for (Field f : fields) {
            ExcelProperty ann = f.getAnnotation(ExcelProperty.class);
            String name = ann.value();
            hs.add((name == null || name.isEmpty()) ? f.getName() : name);
        }
        return hs;
    }

    /**
     * 简单字符串 → 类型转换（支持基本类型、Number、Boolean、String、Date）。
     */
    private static Object convert(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        }
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(value);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(value);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(value);
        }
        if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(value);
        }
        if (targetType == Short.class || targetType == short.class) {
            return Short.valueOf(value);
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return Byte.valueOf(value);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(value);
        }
        if (Number.class.isAssignableFrom(targetType)) {
            // BigDecimal/BigInteger/AtomicInteger/AtomicLong 走 Number 接口
            return new java.math.BigDecimal(value);
        }
        if (targetType == java.time.LocalDate.class) {
            return java.time.LocalDate.parse(value);
        }
        if (targetType == java.time.LocalDateTime.class) {
            return java.time.LocalDateTime.parse(value);
        }
        if (targetType == java.time.LocalTime.class) {
            return java.time.LocalTime.parse(value);
        }
        if (targetType == java.util.Date.class) {
            try {
                return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value);
            } catch (Exception ex) {
                return new java.util.Date(Long.parseLong(value));
            }
        }
        // 兜底：原样返回字符串
        return value;
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.time.LocalDateTime) {
            return value.toString();
        }
        if (value instanceof java.time.LocalDate) {
            return value.toString();
        }
        if (value instanceof java.util.Date) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
        }
        return value.toString();
    }
}
