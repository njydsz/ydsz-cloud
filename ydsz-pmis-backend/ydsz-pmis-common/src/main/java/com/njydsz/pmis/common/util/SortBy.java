package com.njydsz.pmis.common.util;

import org.springframework.data.domain.Sort;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * 类型安全的 Spring Data {@link Sort} 工厂。
 *
 * <p>Spring Data 的 {@link Sort#by(Sort.Direction, String...)} 接受字符串字段名，IDE
 * 插件（Spring Tools）会标记 "Non type-safe property reference" — 字符串字面量与实体
 * 字段名之间无法编译期绑定，实体字段重命名后排序会自动失效。
 *
 * <p>本工具通过 {@link SerializableFunction} + Java {@link SerializedLambda} 反射，
 * 在运行时从 {@code Entity::getXxx} 方法引用中提取字段名（{@code getXxx} → {@code xxx}），
 * 同时保留编译期类型检查。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 旧写法（IDE 警告）
 * PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
 *
 * // 新写法（编译期类型安全）
 * PageRequest.of(0, 20,
 *     SortBy.by(Sort.Direction.DESC, ProjectSearchDoc::getCreatedAt));
 *
 * // 链式：多列排序
 * Sort sort = SortBy.by(Sort.Direction.ASC, ProjectSearchDoc::getProjectName)
 *                   .and(SortBy.by(Sort.Direction.DESC, ProjectSearchDoc::getCreatedAt));
 * }</pre>
 *
 * <h3>支持的 getter 形式</h3>
 * <ul>
 *   <li>{@code Entity::getXxx} — 普通 Bean 规范 getter，提取为 {@code xxx}</li>
 *   <li>{@code Entity::isXxx} — Boolean 字段，提取为 {@code xxx}</li>
 * </ul>
 *
 * <p>本工具不依赖 JPA Metamodel（项目使用 MyBatis-Plus，无 JPA 依赖）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class SortBy {

    private SortBy() {
    }

    /**
     * 创建类型安全的 {@link Sort}（单列）。
     *
     * @param direction 排序方向
     * @param getter    实体 getter 方法引用（{@code Entity::getXxx}）
     * @param <T>       实体类型
     * @param <R>       字段类型
     * @return 包含一列排序的 {@link Sort}
     * @throws IllegalArgumentException getter 解析失败（非实体方法或非 getter 形式）时抛出
     */
    public static <T, R> Sort by(Sort.Direction direction, SerializableFunction<T, R> getter) {
        return Sort.by(order(direction, getter));
    }

    /**
     * 创建类型安全的 {@link Sort.Order}（单列）。
     *
     * @param direction 排序方向
     * @param getter    实体 getter 方法引用
     * @param <T>       实体类型
     * @param <R>       字段类型
     * @return {@link Sort.Order}
     */
    public static <T, R> Sort.Order order(Sort.Direction direction, SerializableFunction<T, R> getter) {
        if (getter == null) {
            throw new IllegalArgumentException("getter 方法引用不能为 null");
        }
        String field = resolveFieldName(getter);
        return new Sort.Order(direction, field);
    }

    /**
     * 降序排序（语法糖，等价 {@code by(Sort.Direction.DESC, getter)}）。
     */
    public static <T, R> Sort desc(SerializableFunction<T, R> getter) {
        return by(Sort.Direction.DESC, getter);
    }

    /**
     * 升序排序（语法糖，等价 {@code by(Sort.Direction.ASC, getter)}）。
     */
    public static <T, R> Sort asc(SerializableFunction<T, R> getter) {
        return by(Sort.Direction.ASC, getter);
    }

    /**
     * 降序 {@link Sort.Order}（语法糖）。
     */
    public static <T, R> Sort.Order orderDesc(SerializableFunction<T, R> getter) {
        return order(Sort.Direction.DESC, getter);
    }

    /**
     * 升序 {@link Sort.Order}（语法糖）。
     */
    public static <T, R> Sort.Order orderAsc(SerializableFunction<T, R> getter) {
        return order(Sort.Direction.ASC, getter);
    }

    /**
     * 通过反射从 lambda 序列化的实现中解析出 getter 字段名。
     *
     * <p>实现原理：{@link SerializableFunction} 的 lambda 实现类在编译后会生成
     * {@link SerializedLambda}，其 {@code getImplMethodName()} 返回方法引用指向的方法名
     * （如 {@code getCreatedAt}），去掉 {@code get}/{@code is} 前缀并首字母小写即为
     * Java Bean 属性名。
     *
     * @param getter 可序列化的 getter 方法引用
     * @return 字段名
     */
    private static String resolveFieldName(Serializable getter) {
        try {
            Method writeReplace = getter.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object lambda = writeReplace.invoke(getter);
            if (!(lambda instanceof SerializedLambda)) {
                throw new IllegalArgumentException(
                        "getter 不是 SerializableFunction（未生成 SerializedLambda）");
            }
            SerializedLambda serialized = (SerializedLambda) lambda;
            String methodName = serialized.getImplMethodName();
            return toFieldName(methodName);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("getter 缺少 writeReplace 方法，非 lambda 实现", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("getter 解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * Java Bean 方法名 → 字段名（{@code getCreatedAt} → {@code createdAt}，{@code isActive} → {@code active}）。
     */
    private static String toFieldName(String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            throw new IllegalArgumentException("getter 方法名不能为空");
        }
        String stripped;
        if (methodName.startsWith("get") && methodName.length() > 3) {
            stripped = methodName.substring(3);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            stripped = methodName.substring(2);
        } else {
            throw new IllegalArgumentException(
                    "getter 必须以 get/is 开头（实际: " + methodName + "）");
        }
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("getter 名称不合法: " + methodName);
        }
        return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
    }
}
