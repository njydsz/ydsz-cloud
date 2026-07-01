/**
 * 混沌工程 API (批次 24 P2-2 chaos-dashboard)
 *
 * 与后端 com.njydsz.pmis.config.controller.ChaosController 对齐。
 *
 * 权限码 (PermissionCodes.SYS_CHAOS_*):
 *   - sys:chaos:view      - 列表/历史
 *   - sys:chaos:create    - 注册/修改
 *   - sys:chaos:delete    - 注销
 *   - sys:chaos:trigger   - 启停/dry-run
 */
import { request } from '@/utils/request'
import type { ChaosExperiment, ChaosEvent, ChaosDryRunResult } from './types'

/** 列出全部已注册实验 */
export const listExperiments = () =>
  request<ChaosExperiment[]>({
    url: '/chaos/experiments',
    method: 'GET',
  })

/** 按 target 查询单个实验 */
export const getExperiment = (target: string) =>
  request<ChaosExperiment>({
    url: `/chaos/experiments/${encodeURIComponent(target)}`,
    method: 'GET',
  })

/** 注册新实验 */
export const registerExperiment = (data: ChaosExperiment) =>
  request<void>({
    url: '/chaos/experiments',
    method: 'POST',
    data,
  })

/** 修改实验 (按 target 覆盖) */
export const updateExperiment = (target: string, data: ChaosExperiment) =>
  request<void>({
    url: `/chaos/experiments/${encodeURIComponent(target)}`,
    method: 'PUT',
    data,
  })

/** 启停实验 */
export const toggleExperiment = (target: string, enabled: boolean) =>
  request<void>({
    url: `/chaos/experiments/${encodeURIComponent(target)}/enabled`,
    method: 'PUT',
    params: { enabled },
  })

/** 注销实验 */
export const unregisterExperiment = (target: string) =>
  request<void>({
    url: `/chaos/experiments/${encodeURIComponent(target)}`,
    method: 'DELETE',
  })

/** 最近 100 条实验历史 */
export const history = () =>
  request<ChaosEvent[]>({
    url: '/chaos/history',
    method: 'GET',
  })

/** 清空历史 */
export const clearHistory = () =>
  request<void>({
    url: '/chaos/history/clear',
    method: 'POST',
  })

/** 主动触发一次注入 (dry-run) */
export const dryRun = (target: string) =>
  request<ChaosDryRunResult>({
    url: '/chaos/dry-run',
    method: 'POST',
    params: { target },
  })
