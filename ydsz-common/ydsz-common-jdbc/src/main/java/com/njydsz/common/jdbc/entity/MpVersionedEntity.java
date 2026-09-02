package com.njydsz.common.jdbc.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 带乐观锁的业务实体基类
 *
 * <p>继承自 {@link MpSimpleEntity}，额外增加乐观锁版本号字段。 适用于高并发更新场景，通过 {@code @Version} 注解让 MyBatis-Plus
 * {@code OptimisticLockerInnerInterceptor} 自动处理版本号递增和冲突检测。
 *
 * <p>继承链：
 *
 * <pre>
 * MpBaseIdEntity (id)
 *   └─ MpBaseAuditEntity (createdAt, createdBy, updatedAt, updatedBy)
 *        └─ MpSimpleEntity (deleted, status, tenantId)
 *             └─ MpVersionedEntity (本类, revision @Version)  ← 有乐观锁
 *                  └─ MpBaseEntity (全功能别名)
 * </pre>
 *
 * <p><b>26.09.01</b>：从 {@link MpBaseEntity} 中拆出乐观锁能力， 使乐观锁成为可选项而非强制项。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class Order extends MpVersionedEntity<Long> {
 *     private BigDecimal amount;
 * }
 * }</pre>
 *
 * @param <T> 主键ID类型
 * @author ydsz-team
 * @since 26.09.01
 * @see MpSimpleEntity
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
public class MpVersionedEntity<T extends Serializable> extends MpSimpleEntity<T> {

  private static final long serialVersionUID = 1L;

  /**
   * 乐观锁版本号
   *
   * <p>每次更新时自动递增（+1），防止并发更新冲突。 由 MyBatis-Plus 原生 {@code OptimisticLockerInnerInterceptor} 处理， 使用
   * {@code @Version} 注解标记，避免自研拦截器维护参数映射的脆弱性。
   *
   * <p>初始值为 0，首次 UPDATE 时自动递增为 1。
   */
  @Version
  @TableField("revision")
  @Builder.Default
  private Integer revision = 0;
}
