/**
 * @file HITL 人工审批 API
 * @description 对应后端 HitlApprovalController (/agent/hitl/approvals)
 * @module api/agent/hitl
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { HitlApprovalRequest, HitlApprovalActionDTO, ReActResult } from './types'

/** 分页查询审批请求 */
export const page = (
  pageNo: number,
  pageSize: number,
  filter: { status?: string; agentType?: string; bizType?: string; bizId?: string } = {},
) =>
  request<{ records: HitlApprovalRequest[]; total: number }>({
    url: '/agent/hitl/approvals/page',
    method: 'GET',
    params: { page: pageNo, size: pageSize, ...filter },
  })

/** 查询待审批请求列表 */
export const pending = (limit = 20) =>
  request<HitlApprovalRequest[]>({
    url: '/agent/hitl/approvals/pending',
    method: 'GET',
    params: { limit },
  })

/** 查询审批请求详情 */
export const getById = (id: string) =>
  request<HitlApprovalRequest>({
    url: `/agent/hitl/approvals/${id}`,
    method: 'GET',
  })

/** 批准审批请求 */
export const approve = (id: string, dto: HitlApprovalActionDTO) =>
  request<ReActResult>({
    url: `/agent/hitl/approvals/${id}/approve`,
    method: 'POST',
    data: dto,
  })

/** 拒绝审批请求 */
export const reject = (id: string, dto: HitlApprovalActionDTO) =>
  request<ReActResult>({
    url: `/agent/hitl/approvals/${id}/reject`,
    method: 'POST',
    data: dto,
  })

/** 取消审批请求 */
export const cancel = (id: string, dto: HitlApprovalActionDTO) =>
  request<void>({
    url: `/agent/hitl/approvals/${id}/cancel`,
    method: 'POST',
    data: dto,
  })
