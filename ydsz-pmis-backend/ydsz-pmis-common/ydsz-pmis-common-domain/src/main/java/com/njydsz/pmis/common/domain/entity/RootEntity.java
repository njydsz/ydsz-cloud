package com.njydsz.pmis.common.domain.entity;

import java.io.Serializable;

/**
 * 实体根接口（向后兼容）�?
 *
 * <p>组合了三个职责单一的子接口，新代码推荐直接使用子接口而非本接口：
 * <ul>
 *   <li>{@link Persistable} —�?ID + isNew()</li>
 *   <li>{@link Versionable} —�?乐观锁版本号</li>
 *   <li>{@link SoftDeletable} —�?逻辑删除</li>
 * </ul>
 *
 * <p><b>审计字段�?/b>审计相关字段（创建人/时间、更新人/时间）已统一收敛�?
 * {@link BaseAuditEntity} 中，由该类实�?{@link Auditable} 接口�?
 * RootEntity 不再组合 Auditable，避免接口默认方法与实体字段重复定义�?
 *
 * <p><b>实体继承层级（v3.5.0 重构后）�?/b>
 * <pre>
 * Persistable&lt;T&gt; ───────────── BaseIdEntity&lt;T&gt;      (�?ID)
 *                                      └── BaseAuditEntity&lt;T&gt;  (+ 审计，实�?Auditable)
 * Versionable  SoftDeletable                └── BaseEntity&lt;T&gt;        (实现 RootEntity: + 乐观�?+ 逻辑删除 + status)
 *     └────── RootEntity&lt;T&gt; (组合接口，向后兼�? ────�?
 *                                     ├── GroupEntity&lt;T&gt;   (+ companyId/deptId)
 *                                     ├── RegionEntity&lt;T&gt;  (+ regionId)
 *                                     ├── TenantEntity&lt;T&gt;  (+ tenantId)
 *                                     ├── ProjectEntity&lt;T&gt; (+ projectId)
 *                                     └── UniqueEntity&lt;T&gt;  (+ uniqueId)
 * </pre>
 *
 * @param <T> 主键ID类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface RootEntity<T extends Serializable>
        extends Persistable<T>, Versionable, SoftDeletable {

    /**
     * 获取实体类型名称
     */
    default String getEntityName() {
        return this.getClass().getSimpleName();
    }
}
