package com.njydsz.common.domain.entity;

import java.io.Serializable;

/**
 * 实体根接口（向后兼容）。
 *
 * <p>组合了三个职责单一的子接口，新代码推荐直接使用子接口而非本接口：
 * <ul>
 *   <li>{@link Persistable} -- ID + isNew()</li>
 *   <li>{@link Versionable} -- 乐观锁版本</li>
 *   <li>{@link SoftDeletable} -- 逻辑删除</li>
 * </ul>
 *
 * <p><b>审计字段：</b>审计相关字段（创建人/时间、更新人/时间）已统一收敛至
 * {@link BaseAuditEntity} 中，由该类实现 {@link Auditable} 接口。
 * RootEntity 不再组合 Auditable，避免接口默认方法与实体字段重复定义。
 *
 * <p><b>实体继承层级：</b>
 * <pre>
 * Persistable&lt;T&gt;
 *   |
 *   +-- BaseIdEntity&lt;T&gt;      (ID)
 *         |
 *         +-- BaseAuditEntity&lt;T&gt;  (+ 审计字段, implements Auditable)
 *               |
 *               +-- BaseEntity&lt;T&gt;        (implements RootEntity + AggregateRoot: + 乐观锁 + 逻辑删除 + status)
 *                     |
 *                     +-- Base              (String 主键)
 *                     +-- BaseLong          (Long 主键)
 *                     +-- LogBase           (日志实体, String 主键)
 * </pre>
 *
 * <p><b>多维度感知：</b>实体可通过实现 {@link TenantAware}、{@link ProjectAware}、
 * {@link RegionAware}、{@link GroupAware} 接口获得多维度数据隔离能力，
 * 无需继承额外的实体基类。
 *
 * @param <T> 主键ID类型
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public interface RootEntity<T extends Serializable>
        extends Persistable<T>, Versionable, SoftDeletable {

    /**
     * 获取实体类型名称
     *
     * @return 实体类的简单名称
     */
    default String getEntityName() {
        return this.getClass().getSimpleName();
    }
}
