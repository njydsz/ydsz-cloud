/**
 * @file Agent 在线调试 API
 * @description 提供 Agent SSE 流式执行、内存执行、同步/异步执行接口，
 *              对应后端 AgentController 中的 /agent/run/stream, /agent/in-memory 等端点。
 * @module api/agent/debug
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { AgentResult } from './types'

/**
 * 内存执行 Agent（不落库），用于即时调试。
 * @param agentType Agent 类型编码
 * @param ctx Agent 执行上下文
 * @returns Agent 执行结果
 */
export const executeInMemory = (agentType: string, ctx: Record<string, unknown>) =>
  request<AgentResult>({
    url: '/agent/in-memory',
    method: 'POST',
    params: { agentType },
    data: ctx,
  })

/**
 * 流式执行 Agent（SSE），逐 token 推送 ReAct 推理过程。
 *
 * 使用 fetch + ReadableStream 方式接收 SSE（因标准 EventSource 仅支持 GET）。
 *
 * @param agentType Agent 类型编码
 * @param ctx Agent 执行上下文
 * @param onEvent SSE 事件回调
 * @param onError 错误回调
 * @param signal AbortSignal，用于取消请求
 * @returns 关闭流的函数
 */
export async function executeStream(
  agentType: string,
  ctx: Record<string, unknown>,
  onEvent: (eventType: string, data: string) => void,
  onError?: (err: Error) => void,
  signal?: AbortSignal,
): Promise<() => void> {
  const baseURL = (import.meta as any).env?.VITE_API_BASE_URL || '/api'
  const token = localStorage.getItem('token') || ''
  const controller = new AbortController()

  if (signal) {
    signal.addEventListener('abort', () => controller.abort())
  }

  try {
    const response = await fetch(`${baseURL}/agent/run/stream?agentType=${encodeURIComponent(agentType)}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: JSON.stringify(ctx),
      signal: controller.signal,
    })

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('No response body')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    // 异步读取流
    ;(async () => {
      try {
        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })

          // 解析 SSE 事件（以 \n\n 分隔）
          const events = buffer.split('\n\n')
          buffer = events.pop() || ''

          for (const eventStr of events) {
            const lines = eventStr.trim().split('\n')
            let eventType = 'message'
            let data = ''
            for (const line of lines) {
              if (line.startsWith('event:')) {
                eventType = line.slice(6).trim()
              } else if (line.startsWith('data:')) {
                data += line.slice(5).trim()
              }
            }
            if (data) {
              onEvent(eventType, data)
            }
          }
        }
      } catch (err: any) {
        if (err.name !== 'AbortError') {
          onError?.(err as Error)
        }
      }
    })()

    return () => controller.abort()
  } catch (err: any) {
    onError?.(err as Error)
    return () => {}
  }
}
