package com.njydsz.agent.entity.base;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 领域层基础实体 — 纯 POJO，不含 MyBatis-Plus 注解。
 *
 * <p>提供与 persistence 层（MpBaseEntity）对齐的字段集合（id、audit fields、tenantId、status 等），
 * 但完全脱离持久化框架注解，保证 domain 层纯净。
 *
 * <p>基础设施层 PO 继承 {@code MpBaseEntity}（含 MP 注解），domain 实体继承本类。
 * 两层之间通过 MapStruct Converter 双向转换。
 *
 * @param <T> 主键类型
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DomainBaseEntity<T extends Serializable> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private T id;

  /** 是否已删除（逻辑删除标记） */
  private Boolean isDeleted;

  /** 记录状态 */
  private String status;

  /** 数据版本号（乐观锁） */
  private Integer revision;

  /** 租户标识（多租户隔离） */
  private String tenantId;

  /** 创建人标识 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人标识 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
