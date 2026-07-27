/**
 * @file 开发环境预热 composable
 * @description 在开发环境下，应用启动后自动触发预热接口，
 *              加速首次请求响应。仅在 dev 模式下激活。
 * @module composables/useDevWarmup
 */
import { ref, onMounted } from 'vue'
import { request } from '@/utils/request'
import { logger } from '@/utils/logger'

/** 预热结果 */
export interface WarmupResult {
  database: {
    status: string
    costMs: number
    userTable?: string
  }
  redis: {
    status: string
    ping?: string
    costMs: number
  }
  jit: {
    status: string
    checksum: number
    costMs: number
  }
}

/**
 * 开发环境预热 composable
 *
 * 仅在 import.meta.env.DEV 为 true 时自动执行预热。
 *
 * @returns `{ isWarmupComplete, warmupResult, warmup }`
 *   - isWarmupComplete: 预热是否已完成（失败也算完成）
 *   - warmupResult: 预热结果数据
 *   - warmup: 手动触发预热
 */
export function useDevWarmup() {
  /** 预热是否已完成（失败也算完成，防止重复执行） */
  const isWarmupComplete = ref(false)
  /** 预热结果数据（成功时包含数据库/Redis/JIT 状态） */
  const warmupResult = ref<WarmupResult | null>(null)

  /** 执行预热请求 */
  async function warmup() {
    if (!import.meta.env.DEV) {
      return
    }

    try {
      logger.info('[DevWarmup] 开始预热...')
      const { data } = await request<WarmupResult>({
        url: '/dev/warmup',
        method: 'POST',
        silent: true,
        timeout: 30000,
      })
      warmupResult.value = data
      isWarmupComplete.value = true
      logger.info('[DevWarmup] 预热完成', data)
    } catch (e) {
      logger.warn('[DevWarmup] 预热失败（不影响正常使用）', e)
      isWarmupComplete.value = true
    }
  }

  onMounted(() => {
    // 延迟 2 秒执行预热，避免与首屏渲染竞争资源
    setTimeout(warmup, 2000)
  })

  return {
    isWarmupComplete,
    warmupResult,
    warmup,
  }
}
