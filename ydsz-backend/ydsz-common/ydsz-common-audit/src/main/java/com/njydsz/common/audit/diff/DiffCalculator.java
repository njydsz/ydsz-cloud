package com.njydsz.common.audit.diff;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 字段差异计算器
 *
 * <p>对比两个同类型实体对象的字段值差异，仅处理标注了 {@link DiffField} 注解的字段。
 * 支持敏感字段脱敏、自定义格式化器、忽略字段等特性。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * UserDO oldUser = userService.getById(id);
 * UserDO newUser = updateUser(oldUser);
 * DiffReport report = DiffCalculator.INSTANCE.calculate(oldUser, newUser);
 * String json = report.toJson(); // 存入操作日志的 diff 字段
 * }</pre>
 *
 * <p><b>注意事项：</b>
 * <ul>
 *   <li>两个对象必须为同一类型，否则抛出 IllegalArgumentException</li>
 *   <li>仅对比标注了 {@link DiffField} 且 ignore=false 的字段</li>
 *   <li>敏感字段会自动脱敏（保留前 2 后 2 位）</li>
 *   <li>字段元数据会被缓存，避免重复反射</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see DiffField
 * @see DiffReport
 */
public class DiffCalculator {

    private static final Logger log = LoggerFactory.getLogger(DiffCalculator.class);

    /** 单例实例 */
    public static final DiffCalculator INSTANCE = new DiffCalculator();

    /** 字段元数据缓存：Class -> List<FieldMeta> */
    private final Map<Class<?>, List<FieldMeta>> fieldMetaCache = new ConcurrentHashMap<>();

    /** 格式化器实例缓存：Class -> Instance */
    private final Map<Class<?>, DiffValueFormatter> formatterCache = new ConcurrentHashMap<>();

    private DiffCalculator() {
    }

    /**
     * 计算两个对象之间的字段差异
     *
     * @param oldObj 变更前对象
     * @param newObj 变更后对象
     * @param <T>    对象类型
     * @return 差异报告
     * @throws IllegalArgumentException 如果两个对象类型不同
     */
    public <T> DiffReport calculate(T oldObj, T newObj) {
        if (oldObj == null && newObj == null) {
            return new DiffReport(List.of());
        }
        if (oldObj == null || newObj == null) {
            throw new IllegalArgumentException("oldObj and newObj must both be non-null or both be null");
        }
        if (oldObj.getClass() != newObj.getClass()) {
            throw new IllegalArgumentException("oldObj and newObj must be of the same type: "
                    + oldObj.getClass().getName() + " vs " + newObj.getClass().getName());
        }

        List<FieldMeta> metas = getFieldMetas(oldObj.getClass());
        List<FieldDiff> diffs = new ArrayList<>();

        for (FieldMeta meta : metas) {
            if (meta.annotation.ignore()) {
                continue;
            }
            try {
                Object oldValue = meta.field.get(oldObj);
                Object newValue = meta.field.get(newObj);

                if (Objects.equals(oldValue, newValue)) {
                    continue;
                }

                String oldStr = formatValue(oldValue, meta);
                String newStr = formatValue(newValue, meta);

                if (meta.annotation.sensitive()) {
                    oldStr = maskSensitive(oldStr);
                    newStr = maskSensitive(newStr);
                }

                String label = meta.annotation.fieldName().isEmpty() ? meta.field.getName() : meta.annotation.fieldName();
                diffs.add(FieldDiff.of(meta.field.getName(), label, oldStr, newStr, meta.annotation.sensitive()));
            } catch (Exception e) {
                log.warn("计算字段差异失败: field={}", meta.field.getName(), e);
            }
        }

        return new DiffReport(diffs);
    }

    /**
     * 获取类的字段元数据（带缓存）
     */
    private List<FieldMeta> getFieldMetas(Class<?> clazz) {
        return fieldMetaCache.computeIfAbsent(clazz, c -> {
            List<FieldMeta> metas = new ArrayList<>();
            Class<?> current = c;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    DiffField annotation = field.getAnnotation(DiffField.class);
                    if (annotation != null) {
                        field.setAccessible(true);
                        metas.add(new FieldMeta(field, annotation));
                    }
                }
                current = current.getSuperclass();
            }
            return List.copyOf(metas);
        });
    }

    /**
     * 格式化字段值
     */
    private String formatValue(Object value, FieldMeta meta) {
        if (value == null) {
            return null;
        }
        DiffValueFormatter formatter = getFormatter(meta.annotation.formatter());
        if (formatter != null && meta.annotation.formatter() != DiffValueFormatter.class) {
            return formatter.format(value);
        }
        return String.valueOf(value);
    }

    /**
     * 获取格式化器实例（带缓存）
     */
    private DiffValueFormatter getFormatter(Class<? extends DiffValueFormatter> formatterClass) {
        if (formatterClass == DiffValueFormatter.class) {
            return null;
        }
        return formatterCache.computeIfAbsent(formatterClass, c -> {
            try {
                return c.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.warn("实例化格式化器失败: {}", c.getName(), e);
                return null;
            }
        });
    }

    /**
     * 敏感字段脱敏（保留前 2 后 2 位）
     */
    private String maskSensitive(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    /**
     * 字段元数据
     */
    private record FieldMeta(Field field, DiffField annotation) {
    }
}
