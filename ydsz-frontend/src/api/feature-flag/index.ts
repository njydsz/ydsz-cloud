/**
 * @file 特性开关 API (批次 20 P2-3)
 * @description 提供特性开关（Feature Flag）的全量快照、按分类聚合、业务方判断、启停、灰度比例设置与缓存刷新能力。
 *
 * 后端接口位于 ydsz-pmis-system 服务 (端口 9001, 2026-07-03 端口重分配):
 *   GET    /feature-flags/snapshot        - 全量快照
 *   GET    /feature-flags/snapshot/grouped - 按分类聚合
 *   GET    /feature-flags/check          - 判断某 flag 是否启用 (业务方)
 *   PUT    /feature-flags/{key}/enabled  - 启停 (admin)
 *   PUT    /feature-flags/{key}/rollout  - 设置灰度 (admin)
 *   POST   /feature-flags/refresh        - 强制刷新缓存 (admin)
 *
 * @module api/feature-flag
 */
import { request } from '@/utils/request'
import type { FeatureFlagSnapshot } from './types'

/**
 * 获取全量 flag 快照 (admin 控制台)
 *
 * 返回所有特性开关的当前配置与生效值。
 *
 * @returns 特性开关快照数组
 */
export const getFeatureFlagSnapshot = () =>
  request<FeatureFlagSnapshot[]>({ url: '/feature-flags/snapshot', method: 'GET' })

/**
 * 按分类聚合快照 (admin 控制台)
 *
 * 以 category 为 key 聚合返回，便于控制台分组展示。
 *
 * @returns key 为分类、value 为该分类下快照数组的对象
 */
export const getFeatureFlagSnapshotGrouped = () =>
  request<Record<string, FeatureFlagSnapshot[]>>({
    url: '/feature-flags/snapshot/grouped',
    method: 'GET',
  })

/**
 * 业务方判断 flag 是否启用 (可选 userId 维度)
 *
 * 业务方调用此接口判断某 flag 是否对当前/指定用户生效（考虑灰度比例）。
 *
 * @param key 特性开关唯一键
 * @param userId 可选用户 ID，用于按用户维度灰度判断
 * @returns 是否启用
 */
export const checkFeatureFlag = (key: string, userId?: number) =>
  request<boolean>({
    url: '/feature-flags/check',
    method: 'GET',
    params: { key, ...(userId !== null && userId !== undefined ? { userId } : {}) },
  })

/**
 * 启停指定 flag (admin)
 *
 * 管理员强制开启/关闭某个特性开关（覆盖灰度策略）。
 *
 * @param key 特性开关唯一键
 * @param enabled 是否启用
 * @returns 是否操作成功
 */
export const setFeatureFlagEnabled = (key: string, enabled: boolean) =>
  request<boolean>({
    url: `/feature-flags/${key}/enabled`,
    method: 'PUT',
    params: { enabled },
  })

/**
 * 设置灰度发布比例 (admin)
 *
 * 设置按用户 ID hash 的灰度放量比例，0 表示全关，100 表示全量。
 *
 * @param key 特性开关唯一键
 * @param percentage 灰度比例 0-100
 * @returns 实际生效的灰度比例
 */
export const setFeatureFlagRollout = (key: string, percentage: number) =>
  request<number>({
    url: `/feature-flags/${key}/rollout`,
    method: 'PUT',
    params: { percentage },
  })

/**
 * 强制刷新本地缓存 (admin)
 *
 * 触发后端主动拉取最新配置并刷新本地缓存。
 *
 * @returns void
 */
export const refreshFeatureFlagCache = () =>
  request<void>({ url: '/feature-flags/refresh', method: 'POST' })
