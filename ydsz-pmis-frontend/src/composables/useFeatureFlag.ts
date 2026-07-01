/**
 * @file 特性开关 composable
 * @description 提供特性开关的响应式判断能力，支持远程拉取 + 本地缓存 + 安全降级
 * @module composables/useFeatureFlag
 *
 * (批次 20 P2-3)
 *
 * 用法:
 *   const { isEnabled, flags, refresh } = useFeatureFlag()
 *
 *   // 单 flag 判断 (自动拉一次)
 *   if (await isEnabled('COCKPIT_V2')) { ... }
 *
 *   // 业务方无感 (首次后本地缓存)
 *   const enabled = useFlag('AGENT_ORCHESTRATION')
 *   if (enabled.value) { ... }
 *
 *   // 用户维度灰度
 *   const enabledForUser = useFlag('NEW_FEATURE', currentUserId)
 */
import { ref, computed, type Ref, type ComputedRef } from 'vue'
import {
  checkFeatureFlag,
  getFeatureFlagSnapshot,
} from '@/api/feature-flag'
import type { FeatureFlagSnapshot } from '@/api/feature-flag/types'

/** 内置的 flag key 枚举 (与后端 FeatureFlag 保持一致) */
export const FEATURE_FLAGS = {
  SENTRY_MONITORING: 'SENTRY_MONITORING',
  DISTRIBUTED_TRACING: 'DISTRIBUTED_TRACING',
  PROMETHEUS_METRICS: 'PROMETHEUS_METRICS',
  CANARY_DEPLOY: 'CANARY_DEPLOY',
  AGENT_ORCHESTRATION: 'AGENT_ORCHESTRATION',
  ADVANCED_PROFIT_SIMULATION: 'ADVANCED_PROFIT_SIMULATION',
  RISK_PREDICTION_ENGINE: 'RISK_PREDICTION_ENGINE',
  AI_RESOURCE_RECOMMEND: 'AI_RESOURCE_RECOMMEND',
  CUSTOMER_CREDIT_SCORING: 'CUSTOMER_CREDIT_SCORING',
  DUAL_RATE_PROFIT: 'DUAL_RATE_PROFIT',
  COCKPIT_V2: 'COCKPIT_V2',
  EXECUTIVE_DASHBOARD: 'EXECUTIVE_DASHBOARD',
  I18N_LOCALIZATION: 'I18N_LOCALIZATION',
  DARK_MODE: 'DARK_MODE',
  AUDIT_LOG_MANDATORY: 'AUDIT_LOG_MANDATORY',
  SENSITIVE_REAUTH: 'SENSITIVE_REAUTH',
  DATA_EXPORT_AUDIT: 'DATA_EXPORT_AUDIT',
  TOTP_TWO_FACTOR: 'TOTP_TWO_FACTOR',
} as const

export type FeatureFlagKey = keyof typeof FEATURE_FLAGS

/** 默认缓存: SAFETY 类视为开启, 其它视为关闭 */
const DEFAULT_SAFETY_FLAGS = new Set<FeatureFlagKey>([
  'AUDIT_LOG_MANDATORY',
  'SENSITIVE_REAUTH',
  'DATA_EXPORT_AUDIT',
  'TOTP_TWO_FACTOR',
])

/** 模块级缓存 */
const _cache = new Map<string, { value: boolean; at: number }>()
const CACHE_TTL_MS = 30_000

function getCached(key: string, userId?: number): boolean | null {
  const cacheKey = `${key}#${userId ?? 'any'}`
  const e = _cache.get(cacheKey)
  if (!e) return null
  if (Date.now() - e.at > CACHE_TTL_MS) return null
  return e.value
}

function setCached(key: string, userId: number | undefined, value: boolean): void {
  const cacheKey = `${key}#${userId ?? 'any'}`
  _cache.set(cacheKey, { value, at: Date.now() })
}

export function useFeatureFlag() {
  /** 全量快照 (响应式) */
  const flags = ref<Record<string, boolean>>({})
  const loading = ref(false)

  /** 异步判断: 远程拉取并缓存 */
  async function isEnabled(
    key: FeatureFlagKey | string,
    userId?: number,
  ): Promise<boolean> {
    const cached = getCached(key, userId)
    if (cached !== null) return cached
    try {
      const resp = await checkFeatureFlag(key, userId)
      const v = !!resp
      setCached(key, userId, v)
      return v
    } catch {
      // 降级: SAFETY 视为开启, 其它关闭
      const def = DEFAULT_SAFETY_FLAGS.has(key as FeatureFlagKey)
      setCached(key, userId, def)
      return def
    }
  }

  /** 拉取全量快照, 写入 flags ref */
  async function refresh(): Promise<void> {
    loading.value = true
    try {
      const resp = (await getFeatureFlagSnapshot()) as unknown as
        | FeatureFlagSnapshot[]
        | { data?: FeatureFlagSnapshot[] }
      const list: FeatureFlagSnapshot[] = Array.isArray(resp)
        ? resp
        : (resp.data ?? [])
      const map: Record<string, boolean> = {}
      for (const s of list) {
        map[s.key] = s.effectiveValue
      }
      flags.value = map
      // 同时刷新缓存
      for (const [k, v] of Object.entries(map)) {
        setCached(k, undefined, v)
      }
    } catch (e) {
      console.warn('[useFeatureFlag] 拉取快照失败, 使用本地默认', e)
      // 降级
      const fallback: Record<string, boolean> = {}
      for (const f of Object.values(FEATURE_FLAGS)) {
        fallback[f] = DEFAULT_SAFETY_FLAGS.has(f as FeatureFlagKey)
      }
      flags.value = fallback
    } finally {
      loading.value = false
    }
  }

  /** 清空缓存 */
  function clearCache(): void {
    _cache.clear()
  }

  return {
    flags: flags as Ref<Record<string, boolean>>,
    loading: loading as Ref<boolean>,
    isEnabled,
    refresh,
    clearCache,
  }
}

/**
 * 同步 reactive 的 flag 判断.
 * 仅依赖 flags ref (需要在父组件调用 useFeatureFlag().refresh() 之后才有值).
 */
export function useFlag(key: FeatureFlagKey | string): ComputedRef<boolean> {
  const { flags } = useFeatureFlag()
  return computed(() => {
    const v = flags.value[key]
    if (v === undefined) {
      // 未拉取过, 降级到 SAFETY 默认
      return DEFAULT_SAFETY_FLAGS.has(key as FeatureFlagKey)
    }
    return v
  })
}

export type FeatureFlagComposable = ReturnType<typeof useFeatureFlag>
