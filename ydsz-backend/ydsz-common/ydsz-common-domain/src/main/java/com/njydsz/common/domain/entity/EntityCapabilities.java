package com.njydsz.common.domain.entity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.ReflectionUtils;

import com.njydsz.common.domain.annotation.CreatedBy;
import com.njydsz.common.domain.annotation.CreateAt;
import com.njydsz.common.domain.annotation.SoftDelete;
import com.njydsz.common.domain.annotation.TenantId;
import com.njydsz.common.domain.annotation.UpdatedBy;
import com.njydsz.common.domain.annotation.UpdateAt;
import com.njydsz.common.domain.annotation.Version;

/**
 * 实体能力检测工具类
 *
 * <p>通过反射检查实体类是否具备特定的领域能力（租户隔离、逻辑删除、审计字段、乐观锁等）。
 * 用于框架层动态判断实体的能力组合，替代深度继承链判断。
 *
 * <p><b>设计目标：</b>
 * 支持扁平化实体设计，实体无需继承多层基类，只需在字段或类上标注对应注解即可具备能力。
 *
 * <p><b>缓存机制：</b>
 * 反射结果使用 {@link ConcurrentHashMap} 缓存，避免重复扫描类层次结构。
 * 缓存键为 (entityClass, annotationClass) 组合，值为 {@link Optional} 包装的 {@link Field}。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 检查是否启用租户隔离
 * if (EntityCapabilities.isTenantIdEnabled(Product.class)) {
 *     // 自动注入 tenant_id 条件
 * }
 *
 * // 检查是否启用逻辑删除
 * if (EntityCapabilities.isSoftDeleteEnabled(Product.class)) {
 *     // 删除转为 UPDATE
 * }
 *
 * // 获取带审计注解的字段
 * Optional<Field> createdBy = EntityCapabilities.getAnnotatedField(Product.class, CreatedBy.class);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public final class EntityCapabilities {

    /**
     * 反射结果缓存：(entityClass + annotationClass) → Optional<Field>
     */
    private static final Map<String, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    /**
     * 软删除注解检测缓存：entityClass → boolean
     */
    private static final Map<Class<?>, Boolean> SOFT_DELETE_CACHE = new ConcurrentHashMap<>();

    /**
     * 乐观锁注解检测缓存：entityClass → boolean
     */
    private static final Map<Class<?>, Boolean> VERSION_CACHE = new ConcurrentHashMap<>();

    /**
     * 软删除字段名常量
     */
    private static final String SOFT_DELETE_FIELD_NAME = "deleted";

    /**
     * 乐观锁字段名常量
     */
    private static final String VERSION_FIELD_NAME = "revision";

    /**
     * MyBatis-Plus Version 注解类缓存
     */
    private static volatile Class<? extends Annotation> mpVersionAnnotationClass;

    private EntityCapabilities() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 检查实体是否启用租户隔离能力
     *
     * @param entityClass 实体类
     * @return 启用租户隔离返回 true，否则返回 false
     */
    public static boolean isTenantIdEnabled(Class<?> entityClass) {
        return getAnnotatedField(entityClass, TenantId.class).isPresent();
    }

    /**
     * 检查实体是否启用逻辑删除能力
     *
     * <p>检测顺序：
     * <ol>
     *   <li>类级 {@link SoftDelete} 注解</li>
     *   <li>字段名约定：存在名为 {@code deleted} 的字段（含父类扫描）</li>
     * </ol>
     *
     * @param entityClass 实体类
     * @return 启用逻辑删除返回 true，否则返回 false
     */
    public static boolean isSoftDeleteEnabled(Class<?> entityClass) {
        return SOFT_DELETE_CACHE.computeIfAbsent(entityClass, clazz -> {
            if (clazz.isAnnotationPresent(SoftDelete.class)) {
                return true;
            }
            // Fallback: field-name convention (deleted)
            return findFieldByName(clazz, SOFT_DELETE_FIELD_NAME) != null;
        });
    }

    /**
     * 检查实体是否具备审计字段能力
     *
     * @param entityClass 实体类
     * @return 具备完整审计字段返回 true，否则返回 false
     */
    public static boolean hasAuditableFields(Class<?> entityClass) {
        return getAnnotatedField(entityClass, CreatedBy.class).isPresent()
                && getAnnotatedField(entityClass, CreateAt.class).isPresent()
                && getAnnotatedField(entityClass, UpdatedBy.class).isPresent()
                && getAnnotatedField(entityClass, UpdateAt.class).isPresent();
    }

    /**
     * 检查实体是否启用乐观锁能力
     *
     * <p>检测顺序：
     * <ol>
     *   <li>字段级自定义 {@link Version} 注解</li>
     *   <li>字段级 MyBatis-Plus {@code @Version} 注解</li>
     *   <li>字段名约定：存在名为 {@code revision} 的字段（含父类扫描）</li>
     * </ol>
     *
     * @param entityClass 实体类
     * @return 启用乐观锁返回 true，否则返回 false
     */
    public static boolean isVersionEnabled(Class<?> entityClass) {
        return VERSION_CACHE.computeIfAbsent(entityClass, clazz -> {
            if (getAnnotatedField(clazz, Version.class).isPresent()) {
                return true;
            }
            Class<? extends Annotation> mpVersion = resolveMpVersionAnnotation();
            if (mpVersion != null && getAnnotatedField(clazz, mpVersion).isPresent()) {
                return true;
            }
            // Fallback: field-name convention (revision)
            return findFieldByName(clazz, VERSION_FIELD_NAME) != null;
        });
    }

    /**
     * 获取实体类中标注了指定注解的字段
     *
     * <p>递归扫描实体类及其所有父类（直到 {@link Object}），
     * 返回第一个匹配的字段。结果使用缓存避免重复反射扫描。
     *
     * @param entityClass     实体类
     * @param annotationClass 目标注解类型
     * @param <A>             注解类型
     * @return 包含匹配字段的 Optional，未找到返回空 Optional
     */
    public static <A extends Annotation> Optional<Field> getAnnotatedField(
            Class<?> entityClass, Class<A> annotationClass) {
        String cacheKey = entityClass.getName() + "#" + annotationClass.getName();
        return FIELD_CACHE.computeIfAbsent(cacheKey, key -> doFindAnnotatedField(entityClass, annotationClass));
    }

    /**
     * 实际执行反射扫描查找标注字段
     *
     * <p>手动遍历类层次结构，找到第一个标注字段后立即返回。
     */
    private static <A extends Annotation> Optional<Field> doFindAnnotatedField(
            Class<?> entityClass, Class<A> annotationClass) {
        Class<?> clazz = entityClass;
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(annotationClass)) {
                    ReflectionUtils.makeAccessible(field);
                    return Optional.of(field);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return Optional.empty();
    }

    /**
     * 按字段名查找字段（含父类扫描）
     *
     * @param entityClass 实体类
     * @param fieldName  字段名
     * @return 找到的 Field，未找到返回 null
     */
    private static Field findFieldByName(Class<?> entityClass, String fieldName) {
        Class<?> clazz = entityClass;
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                return field;
            } catch (NoSuchFieldException e) {
                // continue to parent
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * 解析 MyBatis-Plus Version 注解类（延迟加载 + 缓存）
     *
     * @return MyBatis-Plus Version 注解类，不存在返回 null
     */
    private static Class<? extends Annotation> resolveMpVersionAnnotation() {
        if (mpVersionAnnotationClass != null) {
            return mpVersionAnnotationClass;
        }
        try {
            Class<?> clazz = Class.forName("com.baomidou.mybatisplus.annotation.Version");
            if (Annotation.class.isAssignableFrom(clazz)) {
                mpVersionAnnotationClass = clazz.asSubclass(Annotation.class);
                return mpVersionAnnotationClass;
            }
        } catch (ClassNotFoundException e) {
            // MyBatis-Plus 不在 classpath 中
        }
        return null;
    }

    /**
     * 清除所有缓存（主要用于测试场景）
     */
    public static void clearCache() {
        FIELD_CACHE.clear();
        SOFT_DELETE_CACHE.clear();
        VERSION_CACHE.clear();
        mpVersionAnnotationClass = null;
    }
}
