/**
 * @file 混沌工程 API (批次 24 P2-2 chaos_dashboard)
 * @description 提供混沌实验的注册、查询、启停、注销、历史查询与 dry-run 注入能力。
 *
 * 与后端 com.njydsz.config.controller.ChaosController 对齐。
 *
 * 权限码 (PermissionCodes.SYS_CHAOS_*):
 *   - sys:chaos:view      - 列表/历史
 *   - sys:chaos:create    - 注册/修改
 *   - sys:chaos:delete    - 注销
 *   - sys:chaos:trigger   - 启停/dry-run
 *
 * @module api/chaos
 */
import { request } from '@/utils/request'
import type { ChaosExperiment, ChaosEvent, ChaosDryRunResult } from './types'

/**
 * 列出全部已注册实验
 *
 * 返回当前服务中已注册的全部混沌实验列表。
 *
 * @returns 混沌实验数组
 */
export const listExperiments = () =>
  request<ChaosExperiment[]>({
    url: '/chaos/experiments',
    method: 'GET',
  })

/**
 * 按 target 查询单个实验
 *
 * 根据目标方法/类名前缀查询单个实验配置。
 *
 * @param target 目标方法/类名（前缀匹配）
 * @returns 混沌实验详情
 */
export const getExperiment = (target: string) =>
  request<ChaosExperiment>({
    url: `/chaos/experiments/${encodeURIComponent(target)}`,
    method: 'GET',
  })

/**
 * 注册新实验
 *
 * 注册一个新的混沌实验，target 不能与已有实验重复。
 *
 * @param data 混沌实验配置
 * @returns void
 */
export const registerExperiment = (data: ChaosExperiment) =>
  request<void>({
    url: '/chaos/experiments',
    method: 'POST',
    data,
  })

/**
 * 修改实验 (按 target 覆盖)
 *
 * 按 target 定位并整体覆盖实验配置。
 *
 * @param target 目标方法/类名（前缀匹配）
 * @param data 混沌实验配置
 * @returns void
 */
export const updateExperiment = (target: string, data: ChaosExperiment) =>
  request<void>({
    url: `/chaos/experiments/${encodeURIComponent(target)}`,
    method: 'PUT',
    data,
  })

/**
 * 启停实验
 *
 * 切换实验的 enabled 状态，停用时不再触发注入。
 *
 * @param target 目标方法/类名（前缀匹配）
 * @param enabled 是否启用
 * @returns void
 */
export const toggleExperiment = (target: string, enabled: boolean) =>
  request<void>({
    url: `/chaos/experiments/${encodeURIComponent(target)}/enabled`,
    method: 'PUT',
    params: { enabled },
  })

/**
 * 注销实验
 *
 * 按 target 物理删除实验配置。
 *
 * @param target 目标方法/类名（前缀匹配）
 * @returns void
 */
export const unregisterExperiment = (target: string) =>
  request<void>({
    url: `/chaos/experiments/${encodeURIComponent(target)}`,
    method: 'DELETE',
  })

/**
 * 最近 100 条实验历史
 *
 * 返回最近 100 条实验触发事件，按时间倒序。
 *
 * @returns 混沌事件数组
 */
export const history = () =>
  request<ChaosEvent[]>({
    url: '/chaos/history',
    method: 'GET',
  })

/**
 * 清空历史
 *
 * 物理清空全部混沌实验历史记录。
 *
 * @returns void
 */
export const clearHistory = () =>
  request<void>({
    url: '/chaos/history/clear',
    method: 'POST',
  })

/**
 * 主动触发一次注入 (dry-run)
 *
 * 不落库、不真正改变实验状态，仅模拟一次注入并返回结果，用于验证实验配置。
 *
 * @param target 目标方法/类名（前缀匹配）
 * @returns dry-run 结果（含 outcome 与可能的错误信息）
 */
export const dryRun = (target: string) =>
  request<ChaosDryRunResult>({
    url: '/chaos/dry-run',
    method: 'POST',
    params: { target },
  })
