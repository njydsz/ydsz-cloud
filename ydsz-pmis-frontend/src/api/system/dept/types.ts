import type { BaseVO } from '@/types/api'

export interface DeptVO extends BaseVO {
  id: number
  parentId: number
  deptCode: string
  deptName: string
  deptPath: string
  sortOrder?: number
  leaderId?: number
  leaderName?: string
  phone?: string
  email?: string
  status: string
  children?: DeptVO[]
}

export interface DeptFormDTO {
  id?: number
  parentId: number
  deptCode: string
  deptName: string
  sortOrder?: number
  leaderId?: number
  phone?: string
  email?: string
  status?: string
}
