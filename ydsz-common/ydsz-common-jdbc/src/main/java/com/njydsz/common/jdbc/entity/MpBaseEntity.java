package com.njydsz.common.jdbc.entity;

import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 业务实体统一基类（全功能版本，含乐观锁）
 *
 * <p>继承链：
 * <pre>
 * MpBaseIdEntity (id)
 *   └─ MpBaseAuditEntity (createdAt, createdBy, updatedAt, updatedBy)
 *        └─ MpSimpleEntity (deleted, status, tenantId)  ← 无乐观锁
 *             └─ MpVersionedEntity (revision @Version)    ← 有乐观锁
 *                  └─ MpBaseEntity (本类，别名)
 * </pre>
 *
 * <p>包含主键、审计字段、乐观锁、逻辑删除、状态、租户标识等全部通用列。
 * 业务模块可基于此基类直接继承。
 *
 * <p><b>选型建议：</b>
 * <ul>
 *   <li>需要乐观锁（高并发更新场景）→ 继承 {@link MpBaseEntity} 或 {@link MpVersionedEntity}</li>
 *   <li>不需要乐观锁（纯查询/低频更新场景）→ 继承 {@link MpSimpleEntity}</li>
 *   <li>仅需审计字段 → 继承 {@link MpBaseAuditEntity}</li>
 *   <li>仅需主键 → 继承 {@link MpBaseIdEntity}</li>
 * </ul>
 *
 * <p><b>v1.8.0</b>：{@code @Version} 从 {@link MpSimpleEntity} 拆出到 {@link MpVersionedEntity}，
 * 实体可按需选择是否携带乐观锁能力。本类保持向后兼容，行为与之前版本完全一致。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 高并发场景：需要乐观锁
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class Order extends MpBaseEntity<Long> {
 *     private BigDecimal amount;
 * }
 *
 * // 配置表场景：无需乐观锁
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class Dict extends MpSimpleEntity<Long> {
 *     private String label;
 * }
 * }</pre>
 *
 * @param <T> 主键ID类型
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see MpSimpleEntity
 * @see MpVersionedEntity
 * @see MpBaseAuditEntity
 * @see MpBaseIdEntity
 */
@Getter
@Setter
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MpBaseEntity<T extends Serializable> extends MpVersionedEntity<T> {

    private static final long serialVersionUID = 1L;
}
