/**
 * 后端基础实体类型 — 与 MpBaseEntity 对齐
 *
 * 后端类：com.njydsz.common.jdbc.entity.MpBaseEntity
 */

/** 基础实体字段（所有 DO 继承 MpBaseEntity） */
export interface BaseEntity {
  /** 主键 ID */
  id: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
  /** 创建人 */
  createBy?: string;
  /** 更新人 */
  updateBy?: string;
  /** 逻辑删除标记（0=未删除，1=已删除） */
  deleted?: number | boolean;
}

/** 带租户 ID 的基础实体 */
export interface TenantEntity extends BaseEntity {
  /** 租户 ID */
  tenantId?: string;
}

/** 审计日志基础实体 */
export interface AuditEntity extends BaseEntity {
  /** 审计操作类型 */
  operationType?: string;
  /** 审计操作人 */
  operator?: string;
  /** 审计操作时间 */
  operationTime?: string;
}
