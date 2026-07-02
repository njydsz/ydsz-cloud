/**
 * @file WebSocket 实时推送 composable
 * @description 自动连接、心跳保活、断线重连、按 type 分发消息。
 *   - 连接地址：${网关 host}/ws（网关路由 /ws/** → notification 服务 STOMP 端点）
 *   - 消息格式：{ type: string, data: unknown, timestamp: number }
 *   - 降级：连接失败/断开自动 5s 重连；配合 NotificationBell 的定时轮询兜底
 * @module composables/useWebSocket
 */
import { ref, onMounted, onUnmounted } from 'vue'

/** WebSocket 推送消息体 */
export interface WsMessage {
  type: string
  data: unknown
  timestamp: number
}

/** 心跳间隔（ms） */
const HEARTBEAT_INTERVAL = 30000
/** 重连间隔（ms） */
const RECONNECT_INTERVAL = 5000

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

/**
 * WebSocket 实时推送 composable。
 *
 * 用法：
 * ```ts
 * const { connected, on } = useWebSocket()
 * on('NOTIFICATION', (data) => { ... })
 * ```
 *
 * 注意：需在组件 setup 中调用（内部依赖 onMounted/onUnmounted）。
 */
export function useWebSocket() {
  const connected = ref(false)
  const messages = ref<WsMessage[]>([])
  let ws: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  const handlers = new Map<string, ((data: unknown) => void)[]>()

  const connect = () => {
    try {
      ws = new WebSocket(resolveWsUrl())
      ws.onopen = () => {
        connected.value = true
        startHeartbeat()
      }
      ws.onclose = () => {
        connected.value = false
        stopHeartbeat()
        scheduleReconnect()
      }
      ws.onerror = () => {
        connected.value = false
      }
      ws.onmessage = (event) => {
        try {
          const msg = JSON.parse(event.data) as WsMessage
          messages.value.push(msg)
          const typeHandlers = handlers.get(msg.type)
          if (typeHandlers) {
            typeHandlers.forEach((h) => h(msg.data))
          }
        } catch {
          // 非 JSON 帧忽略（如 SockJS 探针帧）
        }
      }
    } catch {
      scheduleReconnect()
    }
  }

  const startHeartbeat = () => {
    heartbeatTimer = setInterval(() => {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'heartbeat' }))
      }
    }, HEARTBEAT_INTERVAL)
  }

  const stopHeartbeat = () => {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  const scheduleReconnect = () => {
    if (reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, RECONNECT_INTERVAL)
  }

  /** 订阅指定 type 的消息 */
  const on = (type: string, handler: (data: unknown) => void) => {
    if (!handlers.has(type)) {
      handlers.set(type, [])
    }
    handlers.get(type)!.push(handler)
  }

  /** 取消订阅指定 type 的消息 */
  const off = (type: string, handler: (data: unknown) => void) => {
    const typeHandlers = handlers.get(type)
    if (typeHandlers) {
      const idx = typeHandlers.indexOf(handler)
      if (idx > -1) typeHandlers.splice(idx, 1)
    }
  }

  onMounted(() => connect())
  onUnmounted(() => {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    stopHeartbeat()
    if (ws) {
      ws.onclose = null
      ws.close()
    }
  })

  return { connected, messages, on, off }
}
