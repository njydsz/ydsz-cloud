/**
 * @file 资源池 类型定义
 * @description 定义资源池（ResourcePool）相关的 VO 与 DTO 类型，供 pool/index.ts 及上层业务使用。
 * @module api/resource/pool
 */
export interface ResourcePoolVO {
  /** 资源池 ID */
  id: number
  /** 资源池编码 */
  poolCode: string
  /** 资源池名称 */
  poolName: string
  /** 资源池类型：HEADQUARTER（总部）/ DIVISION（事业部）/ BACKUP（储备） */
  poolType: string
  /** 职级范围（如 L1-L3） */
  levelRange?: string
  /** 部门 ID */
  departmentId?: number
  /** 部门名称 */
  departmentName?: string
  /** 容量上限 */
  capacity?: number
  /** 已占用人数 */
  occupiedCount?: number
  /** 池主（经理）ID */
  managerId?: number
  /** 池主（经理）姓名 */
  managerName?: string
  /** 描述 */
  description?: string
  /** 状态 */
  status: string
}

export interface ResourcePoolCreateDTO {
  /** 资源池编码 */
  poolCode: string
  /** 资源池名称 */
  poolName: string
  /** 资源池类型：HEADQUARTER / DIVISION / BACKUP */
  poolType: string
  /** 职级范围（如 L1-L3） */
  levelRange?: string
  /** 部门 ID */
  departmentId?: number
  /** 容量上限 */
  capacity?: number
  /** 池主（经理）ID */
  managerId?: number
  /** 描述 */
  description?: string
  /** 状态 */
  status?: string
}
