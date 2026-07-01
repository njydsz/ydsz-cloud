/**
 * 特性开关 API (批次 20 P2-3)
 *
 * 后端接口位于 ydsz-pmis-config 服务 (端口 9010):
 *   GET    /api/v1/feature-flags/snapshot        - 全量快照
 *   GET    /api/v1/feature-flags/snapshot/grouped - 按分类聚合
 *   GET    /api/v1/feature-flags/check          - 判断某 flag 是否启用 (业务方)
 *   PUT    /api/v1/feature-flags/{key}/enabled  - 启停 (admin)
 *   PUT    /api/v1/feature-flags/{key}/rollout  - 设置灰度 (admin)
 *   POST   /api/v1/feature-flags/refresh        - 强制刷新缓存 (admin)
 */
import { request } from '@/utils/request'
import type { FeatureFlagSnapshot } from './types'

/** 获取全量 flag 快照 (admin 控制台) */
export const getFeatureFlagSnapshot = () =>
  request<FeatureFlagSnapshot[]>({ url: '/feature-flags/snapshot', method: 'GET' })

/** 按分类聚合快照 (admin 控制台) */
export const getFeatureFlagSnapshotGrouped = () =>
  request<Record<string, FeatureFlagSnapshot[]>>({
    url: '/feature-flags/snapshot/grouped',
    method: 'GET',
  })

/** 业务方判断 flag 是否启用 (可选 userId 维度) */
export const checkFeatureFlag = (key: string, userId?: number) =>
  request<boolean>({
    url: '/feature-flags/check',
    method: 'GET',
    params: { key, ...(userId != null ? { userId } : {}) },
  })

/** 启停指定 flag (admin) */
export const setFeatureFlagEnabled = (key: string, enabled: boolean) =>
  request<boolean>({
    url: `/feature-flags/${key}/enabled`,
    method: 'PUT',
    params: { enabled },
  })

/** 设置灰度发布比例 (admin) */
export const setFeatureFlagRollout = (key: string, percentage: number) =>
  request<number>({
    url: `/feature-flags/${key}/rollout`,
    method: 'PUT',
    params: { percentage },
  })

/** 强制刷新本地缓存 (admin) */
export const refreshFeatureFlagCache = () =>
  request<void>({ url: '/feature-flags/refresh', method: 'POST' })
