/**
 * @file WebSocket 实时推送 composable（全局单例）
 * @description 自动连接、心跳保活、断线重连（指数退避）、按 type 分发消息。
 *   - 全局单例：多个组件共享同一个 WS 连接，避免重复创建
 *   - 指数退避：重连间隔从 1s 指数增长，最大 30s，最多 20 次
 *   - 消息容量上限：messages 数组最多保留 100 条，防止内存泄漏
 *   - 组件级自动清理：组件卸载时自动移除该组件注册的所有 handler
 * @module composables/useWebSocket
 */
import { ref, onUnmounted } from 'vue'

/** WebSocket 推送消息体 */
export interface WsMessage {
  type: string
  data: unknown
  timestamp: number
}

/** 心跳间隔（ms） */
const HEARTBEAT_INTERVAL = 30000
/** 初始重连间隔（ms） */
const RECONNECT_INITIAL = 1000
/** 最大重连间隔（ms） */
const RECONNECT_MAX = 30000
/** 最大重连次数 */
const RECONNECT_MAX_ATTEMPTS = 20
/** 消息队列容量上限 */
const MAX_MESSAGES = 100

// ==================== 全局单例状态 ====================
let wsInstance: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
let reconnectAttempts = 0
let isConnecting = false

/** 全局连接状态 */
const globalConnected = ref(false)
/** 全局消息列表（容量上限） */
const globalMessages = ref<WsMessage[]>([])

/** 全局 handler 注册表：Map<type, Set<{handler, componentId}>> */
const globalHandlers = new Map<string, Set<(data: unknown) => void>>()

/** 组件 ID 生成器 */
let componentIdCounter = 0

/**
 * 计算 WebSocket 连接地址。
 * 开发环境前端与网关不同源，优先取 VITE_API_BASE_URL 的 host；生产同源时回退到当前页 host。
 */
function resolveWsUrl(): string {
  const apiBase = import.meta.env.VITE_API_BASE_URL as string | undefined
  if (apiBase) {
    try {
      const u = new URL(apiBase)
      const protocol = u.protocol === 'https:' ? 'wss:' : 'ws:'
      return `${protocol}//${u.host}/ws`
    } catch {
      // apiBase 非法 URL 时回退
    }
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

/** 计算指数退避重连间隔 */
function getReconnectDelay(): number {
  const delay = Math.min(
    RECONNECT_INITIAL * Math.pow(2, reconnectAttempts),
    RECONNECT_MAX
  )
  // 加入随机抖动，防止多个客户端同时重连
  return delay + Math.random() * 500
}

/** 连接 WebSocket */
function connect(): void {
  if (isConnecting || (wsInstance && wsInstance.readyState === WebSocket.OPEN)) {
    return
  }
  isConnecting = true

  try {
    wsInstance = new WebSocket(resolveWsUrl())

    wsInstance.onopen = () => {
      isConnecting = false
      reconnectAttempts = 0
      globalConnected.value = true
      startHeartbeat()
    }

    wsInstance.onclose = () => {
      isConnecting = false
      globalConnected.value = false
      stopHeartbeat()
      scheduleReconnect()
    }

    wsInstance.onerror = () => {
      isConnecting = false
      globalConnected.value = false
    }

    wsInstance.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data) as WsMessage
        // 消息队列容量控制：超过上限移除最旧的消息
        if (globalMessages.value.length >= MAX_MESSAGES) {
          globalMessages.value.shift()
        }
        globalMessages.value.push(msg)

        // 分发到所有注册的 handler
        const typeHandlers = globalHandlers.get(msg.type)
        if (typeHandlers) {
          typeHandlers.forEach((h) => {
            try {
              h(msg.data)
            } catch {
              // 单个 handler 异常不影响其他 handler
            }
          })
        }
      } catch {
        // 非 JSON 帧忽略（如 SockJS 探针帧）
      }
    }
  } catch {
    isConnecting = false
    scheduleReconnect()
  }
}

/** 启动心跳定时器 */
function startHeartbeat(): void {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (wsInstance && wsInstance.readyState === WebSocket.OPEN) {
      try {
        wsInstance.send(JSON.stringify({ type: 'heartbeat' }))
      } catch {
        // 发送失败时关闭连接，触发重连
        wsInstance.close()
      }
    }
  }, HEARTBEAT_INTERVAL)
}

/** 停止心跳定时器 */
function stopHeartbeat(): void {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

/** 调度重连（指数退避） */
function scheduleReconnect(): void {
  if (reconnectTimer) return
  if (reconnectAttempts >= RECONNECT_MAX_ATTEMPTS) {
    console.warn(`[WebSocket] Max reconnection attempts (${RECONNECT_MAX_ATTEMPTS}) reached, giving up`)
    return
  }

  const delay = getReconnectDelay()
  reconnectAttempts++
  console.info(`[WebSocket] Reconnecting in ${Math.round(delay)}ms (attempt ${reconnectAttempts}/${RECONNECT_MAX_ATTEMPTS})`)

  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

/** 确保连接已建立（懒初始化） */
function ensureConnected(): void {
  if (!wsInstance || wsInstance.readyState === WebSocket.CLOSED) {
    connect()
  }
}

/**
 * WebSocket 实时推送 composable（全局单例）。
 *
 * 多个组件调用时共享同一个 WS 连接，组件卸载时自动移除该组件注册的 handler。
 * 当所有组件都卸载后，WS 连接保持（由最后卸载的组件决定是否关闭）。
 *
 * 用法：
 * ```ts
 * const { connected, on, off } = useWebSocket()
 * on('NOTIFICATION', (data) => { ... })
 * ```
 */
export function useWebSocket() {
  const componentId = ++componentIdCounter
  /** 当前组件注册的 handler 列表（用于组件卸载时批量移除） */
  const componentHandlers: Array<{ type: string; handler: (data: unknown) => void }> = []

  /** 订阅指定 type 的消息 */
  const on = (type: string, handler: (data: unknown) => void): void => {
    if (!globalHandlers.has(type)) {
      globalHandlers.set(type, new Set())
    }
    globalHandlers.get(type)!.add(handler)
    componentHandlers.push({ type, handler })
    ensureConnected()
  }

  /** 取消订阅指定 type 的消息 */
  const off = (type: string, handler: (data: unknown) => void): void => {
    const typeHandlers = globalHandlers.get(type)
    if (typeHandlers) {
      typeHandlers.delete(handler)
      if (typeHandlers.size === 0) {
        globalHandlers.delete(type)
      }
    }
    const idx = componentHandlers.findIndex(
      (h) => h.type === type && h.handler === handler
    )
    if (idx > -1) componentHandlers.splice(idx, 1)
  }

  /** 手动重置重连计数（网络恢复后可调用） */
  const resetReconnect = (): void => {
    reconnectAttempts = 0
  }

  // 组件卸载时自动清理该组件注册的所有 handler
  onUnmounted(() => {
    componentHandlers.forEach(({ type, handler }) => {
      const typeHandlers = globalHandlers.get(type)
      if (typeHandlers) {
        typeHandlers.delete(handler)
        if (typeHandlers.size === 0) {
          globalHandlers.delete(type)
        }
      }
    })
    componentHandlers.length = 0
  })

  return {
    connected: globalConnected,
    messages: globalMessages,
    on,
    off,
    resetReconnect,
  }
}
