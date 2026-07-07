/**
 * @file 请求取消管理器
 * @description 基于 AbortController 实现请求级别的取消能力，避免路由切换 / 组件卸载时
 *              未完成请求继续运行导致的数据竞态与内存泄漏。
 *
 * 核心能力：
 *  1. 维护 Map<string, AbortController>，key = `${method}:${url}`（去除 query 参数）
 *  2. addPending: 重复 key 自动取消旧请求，创建新 controller 并写入 config.signal
 *  3. removePending: 请求完成后从 Map 移除
 *  4. cancelAll: 取消所有 pending 请求（路由切换时调用）
 *  5. cancelByUrl: 模糊匹配 url 取消指定请求
 *
 * 可配置：config.skipCancel = true 的请求不纳入取消管理（如轮询、WebSocket、路由守卫自身的请求）
 * @module utils/request-canceler
 */
import type { AxiosRequestConfig } from 'axios'

/**
 * 生成请求唯一 key：`${method}:${url}`（去除 url query 参数）
 *
 * 同一 method + 同一 path 视为同一请求（query 参数变化不区分），
 * 用于在重复请求时取消旧请求。
 */
function getRequestKey(config: AxiosRequestConfig): string {
  const method = (config.method || 'get').toLowerCase()
  const rawUrl = config.url || ''
  // 去除 query 参数，避免分页 / 筛选条件变化被误判为重复请求
  const url = rawUrl.split('?')[0]
  return `${method}:${url}`
}

/**
 * 请求取消管理器
 *
 * 通过单例模式使用，所有 axios 请求共享同一份 pending Map。
 * 在请求拦截器中 addPending，响应（成功/失败）中 removePending，
 * 路由切换 / 组件卸载时调用 cancelAll / cancelByUrl 主动取消。
 */
class RequestCanceler {
  /** pending 请求 Map：key -> AbortController */
  private pendingMap: Map<string, AbortController> = new Map()

  /**
   * 添加 pending 请求
   *
   * - 如果 key 已存在：调用旧 controller.abort() 取消旧请求（避免重复请求竞态）
   * - 创建新 AbortController 并加入 Map
   * - 将 signal 写入 config.signal，供 axios 内部使用
   *
   * @param config - Axios 请求配置（会被原地修改 signal 字段）
   */
  addPending(config: AxiosRequestConfig): void {
    // skipCancel 标记的请求不纳入取消管理
    if (config.skipCancel) return

    const key = getRequestKey(config)

    // key 已存在：取消旧请求（同一接口重复发起时，丢弃上一次未完成的响应）
    const existing = this.pendingMap.get(key)
    if (existing) {
      existing.abort()
      this.pendingMap.delete(key)
    }

    // 创建新 controller 并写入 config.signal
    const controller = new AbortController()
    this.pendingMap.set(key, controller)
    config.signal = controller.signal
  }

  /**
   * 移除 pending 请求（请求完成后调用）
   *
   * 请求正常完成或出错时，从 Map 中移除对应 key，
   * 释放 AbortController 引用，避免内存泄漏。
   *
   * @param config - Axios 请求配置
   */
  removePending(config: AxiosRequestConfig): void {
    if (config.skipCancel) return

    const key = getRequestKey(config)
    this.pendingMap.delete(key)
  }

  /**
   * 取消所有 pending 请求（路由切换时调用）
   *
   * 遍历 Map 调用 abort()，然后 clear() 清空 Map。
   * 取消后 axios 会抛出 CanceledError，由响应拦截器统一静默处理。
   */
  cancelAll(): void {
    this.pendingMap.forEach((controller) => {
      controller.abort()
    })
    this.pendingMap.clear()
  }

  /**
   * 取消指定 URL 的请求（模糊匹配）
   *
   * 遍历 Map，key 中包含传入 url 的请求都会被取消。
   * 用于组件卸载时取消当前组件发起的请求（如表格查询）。
   *
   * @param url - 需要取消的 url 片段（模糊匹配 key 中的 url 部分）
   */
  cancelByUrl(url: string): void {
    this.pendingMap.forEach((controller, key) => {
      // key 格式为 `${method}:${url}`，匹配 url 部分
      if (key.includes(url)) {
        controller.abort()
        this.pendingMap.delete(key)
      }
    })
  }
}

/** 请求取消管理器单例（全局共享 pending Map） */
export const requestCanceler = new RequestCanceler()

export default requestCanceler
