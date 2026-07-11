/**
 * @file 前端断路器 composable
 * @description P1-6: 当某接口连续失败超过阈值时，断路器打开，短路后续请求直接返回错误，
 *   避免对已崩溃的服务发送无效请求。经过冷却期后半开，尝试恢复。
 *
 * 状态机：
 *   CLOSED → (失败率 >= 阈值) → OPEN → (冷却期后) → HALF_OPEN → (成功) → CLOSED
 *                                                      → (失败) → OPEN
 *
 * 使用方式：
 *   const { execute } = useCircuitBreaker('/api/finance/invoice', {
 *     failureThreshold: 5,
 *     cooldownMs: 30000,
 *   })
 *   const data = await execute(() => api.get('/finance/invoice/page'))
 *
 * @module composables/useCircuitBreaker
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import i18n from '@/locales'

type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN'

interface CircuitBreakerOptions {
  /** 失败阈值（连续失败次数达到此值时打开断路器） */
  failureThreshold?: number
  /** 冷却时间（ms），断路器打开后经过此时间进入半开状态 */
  cooldownMs?: number
  /** 半开状态下允许的试探请求数 */
  halfOpenMaxCalls?: number
  /** 断路器打开时的提示消息 i18n key */
  openMessageKey?: string
}

interface CircuitBreakerState {
  state: CircuitState
  failureCount: number
  successCount: number
  lastFailureTime: number
  halfOpenCalls: number
}

const DEFAULT_OPTIONS: Required<CircuitBreakerOptions> = {
  failureThreshold: 5,
  cooldownMs: 30000,
  halfOpenMaxCalls: 3,
  openMessageKey: 'common.circuitBreakerOpen',
}

/** 全局断路器状态（按 key 隔离） */
const circuitStates = new Map<string, CircuitBreakerState>()

/** 全局断路器状态（响应式） */
const circuitOpen = ref(false)

export function useCircuitBreaker(
  key: string,
  options: CircuitBreakerOptions = {},
) {
  const opts = { ...DEFAULT_OPTIONS, ...options }

  /** 获取或初始化断路器状态 */
  function getState(): CircuitBreakerState {
    if (!circuitStates.has(key)) {
      circuitStates.set(key, {
        state: 'CLOSED',
        failureCount: 0,
        successCount: 0,
        lastFailureTime: 0,
        halfOpenCalls: 0,
      })
    }
    return circuitStates.get(key)!
  }

  /** 检查断路器是否允许请求通过 */
  function canExecute(): boolean {
    const state = getState()

    if (state.state === 'CLOSED') {
      return true
    }

    if (state.state === 'OPEN') {
      // 检查是否已过冷却期
      const elapsed = Date.now() - state.lastFailureTime
      if (elapsed >= opts.cooldownMs) {
        state.state = 'HALF_OPEN'
        state.halfOpenCalls = 0
        return true
      }
      return false
    }

    // HALF_OPEN 状态：允许有限的试探请求
    if (state.halfOpenCalls < opts.halfOpenMaxCalls) {
      state.halfOpenCalls++
      return true
    }
    return false
  }

  /** 记录成功 */
  function recordSuccess(): void {
    const state = getState()
    if (state.state === 'HALF_OPEN') {
      state.successCount++
      // 半开状态下连续成功，恢复到 CLOSED
      if (state.successCount >= opts.halfOpenMaxCalls) {
        state.state = 'CLOSED'
        state.failureCount = 0
        state.successCount = 0
        circuitOpen.value = false
      }
    } else if (state.state === 'CLOSED') {
      // 成功时重置失败计数
      state.failureCount = 0
    }
  }

  /** 记录失败 */
  function recordFailure(): void {
    const state = getState()
    state.lastFailureTime = Date.now()

    if (state.state === 'HALF_OPEN') {
      // 半开状态失败，重新打开断路器
      state.state = 'OPEN'
      state.failureCount = opts.failureThreshold
      state.successCount = 0
      circuitOpen.value = true
      showOpenMessage()
    } else if (state.state === 'CLOSED') {
      state.failureCount++
      if (state.failureCount >= opts.failureThreshold) {
        state.state = 'OPEN'
        circuitOpen.value = true
        showOpenMessage()
      }
    }
  }

  /** 显示断路器打开提示 */
  function showOpenMessage(): void {
    ElMessage.warning(i18n.global.t(opts.openMessageKey))
  }

  /**
   * 执行受断路器保护的请求
   *
   * @param fn 请求函数（返回 Promise）
   * @returns 请求结果
   * @throws Error 断路器打开时抛出错误
   */
  async function execute<T>(fn: () => Promise<T>): Promise<T> {
    if (!canExecute()) {
      throw new Error(`Circuit breaker is OPEN for ${key}`)
    }

    try {
      const result = await fn()
      recordSuccess()
      return result
    } catch (error) {
      recordFailure()
      throw error
    }
  }

  /** 手动重置断路器 */
  function reset(): void {
    circuitStates.set(key, {
      state: 'CLOSED',
      failureCount: 0,
      successCount: 0,
      lastFailureTime: 0,
      halfOpenCalls: 0,
    })
    circuitOpen.value = false
  }

  /** 获取当前状态 */
  function getStateInfo() {
    const state = getState()
    return {
      state: state.state,
      failureCount: state.failureCount,
      isOpen: state.state === 'OPEN',
    }
  }

  return {
    execute,
    reset,
    getStateInfo,
    circuitOpen,
  }
}
