/**
 * @file WebSocket 实时推送 composable（全局单例，基于 STOMP 协议）
 * @description P0-1: 使用 @stomp/stompjs 替代原生 WebSocket，与后端 Spring STOMP broker 直连。
 *   - 全局单例：多个组件共享同一个 STOMP 连接，避免重复创建
 *   - 内置心跳：STOMP 协议层 10s/10s 心跳保活（服务端/客户端各 10s）
 *   - 自动重连：@stomp/stompjs 内置指数退避重连（默认 5s 间隔）
 *   - 按 type 分发：后端推送的 {type, data, timestamp} 消息按 type 字段路由到 handler
 *   - 组件级自动清理：组件卸载时自动移除该组件注册的所有 handler
 * @module composables/useWebSocket
 */
import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import { getToken } from '@/utils/auth'

/** WebSocket 推送消息体 */
export interface WsMessage {
  type: string
  data: unknown
  timestamp: number
}

/** 消息队列容量上限 */
const MAX_MESSAGES = 100

// ==================== 全局单例状态 ====================
let stompClient: Client | null = null
/** STOMP 订阅对象（用于断线重连后重新订阅） */
let stompSubscription: { unsubscribe: () => void } | null = null

/** 全局连接状态 */
const globalConnected = ref(false)
/** 全局消息列表（容量上限） */
const globalMessages = ref<WsMessage[]>([])

/** 全局 handler 注册表：Map<type, Set<handler>> */
const globalHandlers = new Map<string, Set<(data: unknown) => void>>()

/** 组件 ID 生成器 */
let componentIdCounter = 0

/**
 * 计算 STOMP broker 连接地址。
 * 开发环境前端与网关不同源，优先取 VITE_API_BASE_URL 的 host；生产同源时回退到当前页 host。
 */
function resolveBrokerUrl(): string {
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
 * 从 localStorage 获取当前用户 ID（避免循环依赖 Pinia store）。
 * user store 在 fetchUserInfo 后会将 userInfo 持久化到 localStorage。
 */
function getCurrentUserId(): string | null {
  try {
    const raw = localStorage.getItem('userInfo')
    if (raw) {
      const info = JSON.parse(raw)
      if (info && info.id) return String(info.id)
    }
  } catch {
    // ignore
  }
  return null
}

/** 初始化 STOMP 客户端（懒初始化，仅创建一次） */
function initClient(): void {
  if (stompClient) return

  stompClient = new Client({
    brokerURL: resolveBrokerUrl(),
    // 心跳间隔 10s（对标后端 WebSocketConfig 配置）
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    // 重连延迟 5s（@stomp/stompjs 内置自动重连）
    reconnectDelay: 5000,

    // 连接前携带 JWT token（通过 STOMP CONNECT 帧 header 传递）
    connectHeaders: {
      Authorization: getToken() || '',
    },

    onConnect: () => {
      globalConnected.value = true

      // 订阅当前用户的私有通知频道
      const userId = getCurrentUserId()
      if (userId) {
        stompSubscription = stompClient!.subscribe(
          `/topic/user/${userId}/notifications`,
          (frame) => {
            try {
              const msg = JSON.parse(frame.body) as WsMessage
              // 消息队列容量控制
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
              // 非 JSON 帧忽略
            }
          },
        )
      }
    },

    onDisconnect: () => {
      globalConnected.value = false
      if (stompSubscription) {
        stompSubscription.unsubscribe()
        stompSubscription = null
      }
    },

    onStompError: (frame) => {
      console.error('[STOMP] Broker error:', frame.headers['message'], frame.body)
    },

    onWebSocketError: () => {
      globalConnected.value = false
    },
  })
}

/** 确保 STOMP 连接已激活（懒初始化） */
function ensureConnected(): void {
  if (!stompClient) {
    initClient()
  }
  if (!stompClient!.active) {
    stompClient!.activate()
  }
}

/**
 * WebSocket 实时推送 composable（全局单例）。
 *
 * 多个组件调用时共享同一个 STOMP 连接，组件卸载时自动移除该组件注册的 handler。
 *
 * 用法：
 * ```ts
 * const { connected, on, off } = useWebSocket()
 * on('TODO_COUNT', (data) => { ... })
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
      (h) => h.type === type && h.handler === handler,
    )
    if (idx > -1) componentHandlers.splice(idx, 1)
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
  }
}
