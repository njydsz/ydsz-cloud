package com.njydsz.pmis.common.domain.entity;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Optional;

import org.springframework.util.ReflectionUtils;

import com.njydsz.pmis.common.domain.annotation.*;

/**
 * 实体能力检测工具类
 *
 * <p>通过反射检查实体类是否具备特定的领域能力（租户隔离、逻辑删除、审计字段、乐观锁等）。
 * 用于框架层动态判断实体的能力组合，替代深度继承链判断。
 *
 * <p><b>设计目标：</b>
 * 支持扁平化实体设计，实体无需继承多层基类，只需在字。类上标注对应注解即可具备能力。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 检查是否启用租户隔。
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public final class EntityCapabilities {

    private EntityCapabilities() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 检查实体是否启用租户隔离能力
     *
     * <p>判断依据：实体中存在与 {@link TenantId} 注解标记的字段名
     *
     * @param entityClass 实体。
     * @return 启用租户隔离返回 true，否则返。false
     */
    public static boolean isTenantIdEnabled(Class<?> entityClass) {
        return getAnnotatedField(entityClass, TenantId.class).isPresent();
    }

    /**
     * 检查实体是否启用逻辑删除能力
     *
     * <p>判断依据：实体类上标注了 {@link SoftDelete} 注解。
     *
     * @param entityClass 实体。
     * @return 启用逻辑删除返回 true，否则返。false
     */
    public static boolean isSoftDeleteEnabled(Class<?> entityClass) {
        return entityClass.isAnnotationPresent(SoftDelete.class);
    }

    /**
     * 检查实体是否具备审计字段能力
     *
     * <p>判断依据：实体中同时存在与 {@link CreatedBy}、{@link CreateTime}。
     * {@link UpdatedBy}、{@link UpdateTime} 注解标记的字段名
     *
     * @param entityClass 实体。
     * @return 具备完整审计字段返回 true，否则返。false
     */
    public static boolean hasAuditableFields(Class<?> entityClass) {
        return getAnnotatedField(entityClass, CreatedBy.class).isPresent()
                && getAnnotatedField(entityClass, CreateTime.class).isPresent()
                && getAnnotatedField(entityClass, UpdatedBy.class).isPresent()
                && getAnnotatedField(entityClass, UpdateTime.class).isPresent();
    }

    /**
     * 检查实体是否启用乐观锁能力
     *
     * <p>判断依据：实体中存在与 {@link Version} 。MyBatis-Plus
     * {@code com.baomidou.mybatisplus.annotation.Version} 注解标记的字段名
     * 优先检查自定义注解，若未找到再检。MyBatis-Plus 注解。
     *
     * @param entityClass 实体。
     * @return 启用乐观锁返。true，否则返。false
     */
    public static boolean isVersionEnabled(Class<?> entityClass) {
        if (getAnnotatedField(entityClass, Version.class).isPresent()) {
            return true;
        }
        try {
            Class<?> mpVersionClass = Class.forName("com.baomidou.mybatisplus.annotation.Version");
            Class<? extends Annotation> annotationClass =
                    (Class<? extends Annotation>) mpVersionClass;
            return getAnnotatedField(entityClass, annotationClass).isPresent();
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 获取实体中被指定注解标记的字。
     *
     * <p>递归扫描实体类及其所有父类（直到 {@link Object}），
     * 返回第一个匹配的字段名
     *
     * @param entityClass     实体。
     * @param annotationClass 目标注解类型
     * @param <A>             注解类型
     * @return 包含匹配字段名Optional，未找到返回。Optional
     */
    public static <A extends Annotation> Optional<Field> getAnnotatedField(Class<?> entityClass, Class<A> annotationClass) {
        final Field[] found = {null};
        final boolean[] stopped = {false};

        ReflectionUtils.doWithFields(entityClass, field -> {
            if (!stopped[0] && field.isAnnotationPresent(annotationClass)) {
                found[0] = field;
                stopped[0] = true;
            }
        });

        return Optional.ofNullable(found[0]);
    }
}
