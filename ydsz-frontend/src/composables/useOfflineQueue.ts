/**
 * 离线操作队列 composable（P2-4）
 *
 * <p>当网络断开时，将用户的写操作（如提交表单、保存数据）暂存到本地队列，
 * 网络恢复后自动重放队列中的操作，确保用户操作不丢失。
 *
 * <p>核心能力：
 * <ul>
 *   <li>监听 online/offline 事件，实时感知网络状态</li>
 *   <li>离线时的写操作自动入队（通过包装函数）</li>
 *   <li>网络恢复后自动重放队列（FIFO 顺序）</li>
 *   <li>支持操作去重（基于 operationId）</li>
 *   <li>持久化到 localStorage，刷新页面不丢失</li>
 * </ul>
 *
 * <p>使用方式：
 * ```ts
 * const { isOnline, enqueue, pendingCount } = useOfflineQueue()
 *
 * // 在提交按钮中：
 * async function handleSubmit() {
 *   await enqueue({
 *     id: `save-project-${Date.now()}`,
 *     execute: () => api.post('/project/save', formData),
 *   })
 * }
 * ```
 *
 * @author ydsz-team
 * @since 1.0.0
 */

import { ref, computed, onMounted, onUnmounted } from 'vue'

interface QueuedOperation {
  /** 操作唯一 ID（用于去重） */
  id: string
  /** 操作类型（如 SAVE / DELETE / SUBMIT） */
  type: string
  /** API URL */
  url: string
  /** HTTP 方法 */
  method: 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  /** 请求体 JSON */
  body?: string
  /** 入队时间 */
  timestamp: number
  /** 重试次数 */
  retryCount: number
}

const STORAGE_KEY = 'ydsz_offline_queue'
const MAX_RETRIES = 3

const isOnline = ref(navigator.onLine)
const queue = ref<QueuedOperation[]>([])
const isProcessing = ref(false)

/** 持久化队列到 localStorage */
function persistQueue(): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(queue.value))
  } catch {
    // localStorage 满或不可用时静默失败
  }
}

/** 从 localStorage 恢复队列 */
function restoreQueue(): void {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored) {
      queue.value = JSON.parse(stored)
    }
  } catch {
    // 解析失败时清空
    localStorage.removeItem(STORAGE_KEY)
  }
}

/** 处理 online 事件 */
function handleOnline(): void {
  isOnline.value = true
  // 自动重放队列
  processQueue()
}

/** 处理 offline 事件 */
function handleOffline(): void {
  isOnline.value = false
}

/**
 * 将操作加入离线队列
 *
 * @param operation 操作信息
 * @returns Promise，在线时立即执行，离线时等待网络恢复后执行
 */
function enqueue(operation: Omit<QueuedOperation, 'timestamp' | 'retryCount'>): Promise<void> {
  return new Promise((resolve, reject) => {
    const queuedOp: QueuedOperation = {
      ...operation,
      timestamp: Date.now(),
      retryCount: 0,
    }

    // 去重：如果队列中已有相同 ID 的操作，移除旧的
    queue.value = queue.value.filter(op => op.id !== operation.id)

    if (isOnline.value) {
      // 在线：直接执行
      executeOperation(queuedOp)
        .then(() => resolve())
        .catch((err) => {
          // 执行失败，加入队列重试
          queue.value.push(queuedOp)
          persistQueue()
          reject(err)
        })
    } else {
      // 离线：加入队列
      queue.value.push(queuedOp)
      persistQueue()
      // 等待网络恢复（不立即 reject）
      const checkInterval = setInterval(() => {
        if (isOnline.value && !queue.value.find(op => op.id === operation.id)) {
          clearInterval(checkInterval)
          resolve()
        }
      }, 1000)
      // 30 秒超时
      setTimeout(() => {
        clearInterval(checkInterval)
        if (!isOnline.value) {
          reject(new Error('网络不可用，操作已暂存到离线队列'))
        }
      }, 30000)
    }
  })
}

/** 执行单个操作 */
async function executeOperation(op: QueuedOperation): Promise<void> {
  const response = await fetch(op.url, {
    method: op.method,
    headers: { 'Content-Type': 'application/json' },
    body: op.body,
  })
  if (!response.ok) {
    throw new Error(`操作失败: ${response.status} ${response.statusText}`)
  }
}

/** 处理队列（网络恢复后调用） */
async function processQueue(): Promise<void> {
  if (isProcessing.value || queue.value.length === 0) return

  isProcessing.value = true
  const operations = [...queue.value]

  for (const op of operations) {
    try {
      await executeOperation(op)
      // 成功：从队列中移除
      queue.value = queue.value.filter(q => q.id !== op.id)
      persistQueue()
    } catch (err) {
      // 失败：增加重试次数
      const idx = queue.value.findIndex(q => q.id === op.id)
      if (idx >= 0) {
        queue.value[idx].retryCount++
        if (queue.value[idx].retryCount >= MAX_RETRIES) {
          // 达到最大重试次数，移除并记录
          console.error('[OfflineQueue] 操作达到最大重试次数，已移除:', op)
          queue.value.splice(idx, 1)
        }
      }
      persistQueue()
      // 某个操作失败时停止处理，等待下次网络恢复
      break
    }
  }

  isProcessing.value = false
}

/**
 * 离线操作队列 composable
 */
export function useOfflineQueue() {
  const pendingCount = computed(() => queue.value.length)

  onMounted(() => {
    restoreQueue()
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    // 如果已在线且有积压操作，立即处理
    if (isOnline.value && queue.value.length > 0) {
      processQueue()
    }
  })

  onUnmounted(() => {
    window.removeEventListener('online', handleOnline)
    window.removeEventListener('offline', handleOffline)
  })

  return {
    /** 当前网络是否在线 */
    isOnline,
    /** 待处理的操作数 */
    pendingCount,
    /** 将操作加入队列 */
    enqueue,
    /** 手动触发队列处理 */
    processQueue,
    /** 清空队列 */
    clearQueue: () => {
      queue.value = []
      persistQueue()
    },
  }
}
