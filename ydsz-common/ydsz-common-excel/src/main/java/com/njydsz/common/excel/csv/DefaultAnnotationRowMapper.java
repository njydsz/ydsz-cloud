package com.njydsz.common.excel.csv;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor;
import com.njydsz.common.excel.support.cache.ReflectCache;
import com.njydsz.common.excel.tabular.TabularRowMapper;
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

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
     * 静态工厂方法。
     *
     * <p>每次调用构造一个新的 Mapper 实例。{@code DefaultAnnotationRowMapper}
     * 不再使用全局缓存，因为泛型类型 {@code Class<T> → DefaultAnnotationRowMapper<T>}
     * 在 Java 类型系统中无法以类型安全的方式缓存（需要未经检查的强制类型转换）。
     * 如需复用 Mapper 实例，由调用方自行持有引用。
     *
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 新构造的 Mapper 实例
     */
    public static <T> DefaultAnnotationRowMapper<T> of(Class<T> clazz) {
        return new DefaultAnnotationRowMapper<>(clazz);
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
            return new BigDecimal(value);
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse(value);
        }
        if (targetType == LocalDateTime.class) {
            return LocalDateTime.parse(value);
        }
        if (targetType == LocalTime.class) {
            return LocalTime.parse(value);
        }
        if (targetType == Date.class) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(value, DATE_TIME_FORMATTER);
                return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception ex) {
                return new Date(Long.parseLong(value));
            }
        }
        // 兜底：原样返回字符串
        return value;
    }

    private static String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime) {
            return value.toString();
        }
        if (value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof Date) {
            return DATE_TIME_FORMATTER.format(
                    ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        }
        return value.toString();
    }
}
