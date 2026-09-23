package com.njydsz.agent.entity.base;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 领域层基础审计实体 — 纯 POJO，不含 MyBatis-Plus 注解。
 *
 * <p>提供与 persistence 层 {@code MpBaseAuditEntity} 对齐的最小字段集合（id + audit fields），
 * 不含 deleted / status / revision / tenantId 等扩展字段。
 *
 * <p>适用实体：InsightReport、UserProfile 等仅需审计追踪而无需逻辑删除的聚合根。
 *
 * @param <T> 主键类型
 * @author ydsz-team
 * @since 26.09.23
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DomainAuditBaseEntity<T extends Serializable> implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private T id;

  /** 创建人标识 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人标识 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
