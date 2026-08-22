package com.njydsz.common.jdbc.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.json.annotation.JsonIgnore;

/**
 * 业务实体无锁基类（不含乐观锁）
 *
 * <p>继承自 {@link MpBaseAuditEntity}，包含逻辑删除、状态、租户标识字段，
 * 但<b>不含</b>乐观锁版本号。适用于不需要并发更新保护的场景（如配置表、字典表、日志表等）， 避免不必要的版本号字段和 UPDATE 时的版本递增开销。
 *
 * <p>继承链：
 *
 * <pre>
 * MpBaseIdEntity (id)
 *   └─ MpBaseAuditEntity (createdAt, createdBy, updatedAt, updatedBy)
 *        └─ MpSimpleEntity (本类, deleted, status, tenantId)  ← 无乐观锁
 *             └─ MpVersionedEntity (revision @Version)
 *                  └─ MpBaseEntity (全功能别名)
 * </pre>
 *
 * <p><b>逻辑删除：</b>deleted 字段使用 MP 原生 {@code @TableLogic} 注解， 由 MyBatis-Plus 自动处理 SELECT 条件追加和 DELETE
 * 转 UPDATE。
 *
 * <p><b>1.0.0</b>：逻辑删除从自研 {@code LogicalDeleteInterceptor} 迁移至 {@code @TableLogic} 注解。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class Dict extends MpSimpleEntity<Long> {
 *     private String label;
 *     private String value;
 * }
 * }</pre>
 *
 * @param <T> 主键ID类型
 * @author ydsz-team
 * @since 1.0.0
 * @see MpVersionedEntity
 * @see MpBaseEntity
 * @see MpBaseAuditEntity
 */
@Getter
@Setter
@ToString(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(callSuper = true)
public class MpSimpleEntity<T extends Serializable> extends MpBaseAuditEntity<T> {

  private static final long serialVersionUID = 1L;

  /**
   * 逻辑删除标识
   *
   * <p>0=未删除，1=已删除。使用 MP 原生 {@code @TableLogic} 注解， 由 MyBatis-Plus 自动处理逻辑删除条件。
   */
  @TableLogic
  @TableField("deleted")
  @JsonIgnore
  private Integer deleted;

  /**
   * 状态标识
   *
   * <p>子类可按需覆盖为具体业务状态枚举值，默认值为空。
   */
  @TableField("status")
  private String status;

  /**
   * 租户 ID
   *
   * <p>多租户隔离字段，由 SQL 拦截器自动注入 WHERE 条件和 INSERT 填充。 对外 API 不暴露租户 ID。
   *
   * <p><b>注意：</b>此字段始终存在于 {@code MpSimpleEntity} 中。 当未启用多租户时，此字段被忽略（DDL 默认值 '1'），不会影响业务逻辑。
   */
  @TableField("tenant_id")
  @JsonIgnore
  private String tenantId;
}
