/**
 * @file request-canceler.test.ts
 * @description 测试请求取消管理器（AbortController）的核心能力：
 *              addPending / removePending / cancelAll / cancelByUrl / skipCancel
 * @vitest-environment jsdom
 */
import { describe, it, expect, beforeEach } from 'vitest'
import type { AxiosRequestConfig } from 'axios'
// 引入 request 模块以使 declare module 'axios' 中的 skipCancel 类型增强生效
import '@/utils/request'
import { requestCanceler } from '@/utils/request-canceler'

describe('requestCanceler - 请求取消管理器', () => {
  beforeEach(() => {
    // 每个测试前清空 pending Map，保证测试隔离（单例状态在测试间共享）
    requestCanceler.cancelAll()
  })

  describe('addPending', () => {
    it('应创建 AbortController 并写入 config.signal', () => {
      const config: AxiosRequestConfig = { method: 'GET', url: '/users/me' }

      requestCanceler.addPending(config)

      // signal 已写入且未被取消
      expect(config.signal).toBeDefined()
      expect(config.signal!.aborted).toBe(false)
    })

    it('重复 key 应取消旧请求', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      const config2: AxiosRequestConfig = { method: 'GET', url: '/users/me' }

      requestCanceler.addPending(config1)
      requestCanceler.addPending(config2)

      // 旧请求被取消，新请求未取消
      expect(config1.signal!.aborted).toBe(true)
      expect(config2.signal!.aborted).toBe(false)
    })

    it('不同 method 或 url 不视为重复请求', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      const config2: AxiosRequestConfig = { method: 'POST', url: '/users/me' }
      const config3: AxiosRequestConfig = { method: 'GET', url: '/projects/list' }

      requestCanceler.addPending(config1)
      requestCanceler.addPending(config2)
      requestCanceler.addPending(config3)

      expect(config1.signal!.aborted).toBe(false)
      expect(config2.signal!.aborted).toBe(false)
      expect(config3.signal!.aborted).toBe(false)
    })

    it('url 的 query 参数不参与 key 生成（同一 path 视为重复请求）', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me?page=1' }
      const config2: AxiosRequestConfig = { method: 'GET', url: '/users/me?page=2' }

      requestCanceler.addPending(config1)
      requestCanceler.addPending(config2)

      // query 不同但 path 相同 → 视为重复请求，旧请求被取消
      expect(config1.signal!.aborted).toBe(true)
    })

    it('method 缺省时按 get 处理', () => {
      const config1: AxiosRequestConfig = { url: '/users/me' }
      const config2: AxiosRequestConfig = { method: 'get', url: '/users/me' }

      requestCanceler.addPending(config1)
      requestCanceler.addPending(config2)

      // 缺省 method 等价于 get，视为重复请求
      expect(config1.signal!.aborted).toBe(true)
    })
  })

  describe('removePending', () => {
    it('应从 Map 中移除指定 key（移除后不受 cancelAll 影响）', () => {
      const config: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      requestCanceler.addPending(config)

      requestCanceler.removePending(config)

      // 移除后，cancelAll 不应影响到该 signal（已不在 Map 中）
      requestCanceler.cancelAll()
      expect(config.signal!.aborted).toBe(false)
    })

    it('移除不存在的 key 不报错', () => {
      const config: AxiosRequestConfig = { method: 'GET', url: '/not-exist' }

      expect(() => requestCanceler.removePending(config)).not.toThrow()
    })
  })

  describe('cancelAll', () => {
    it('应取消所有 pending 请求', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      const config2: AxiosRequestConfig = { method: 'POST', url: '/projects' }
      const config3: AxiosRequestConfig = { method: 'DELETE', url: '/items/1' }
      requestCanceler.addPending(config1)
      requestCanceler.addPending(config2)
      requestCanceler.addPending(config3)

      requestCanceler.cancelAll()

      expect(config1.signal!.aborted).toBe(true)
      expect(config2.signal!.aborted).toBe(true)
      expect(config3.signal!.aborted).toBe(true)
    })

    it('应清空 Map（cancelAll 后新增请求不会被立即取消）', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      requestCanceler.addPending(config1)

      requestCanceler.cancelAll()

      // Map 已清空：再次添加新请求，不应被任何遗留逻辑取消
      const config2: AxiosRequestConfig = { method: 'GET', url: '/projects' }
      requestCanceler.addPending(config2)
      expect(config2.signal!.aborted).toBe(false)
    })

    it('空 Map 时调用不报错', () => {
      expect(() => requestCanceler.cancelAll()).not.toThrow()
    })
  })

  describe('cancelByUrl', () => {
    it('应模糊匹配 url 取消对应请求', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      const config2: AxiosRequestConfig = { method: 'GET', url: '/projects/list' }
      const config3: AxiosRequestConfig = { method: 'POST', url: '/users/update' }
      requestCanceler.addPending(config1)
      requestCanceler.addPending(config2)
      requestCanceler.addPending(config3)

      // 取消所有 key 中包含 /users 的请求
      requestCanceler.cancelByUrl('/users')

      expect(config1.signal!.aborted).toBe(true)
      expect(config3.signal!.aborted).toBe(true)
      // /projects/list 不匹配 /users，保持 pending
      expect(config2.signal!.aborted).toBe(false)
    })

    it('匹配的请求从 Map 中移除，未匹配的保留', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      const config2: AxiosRequestConfig = { method: 'GET', url: '/projects/list' }
      requestCanceler.addPending(config1)
      requestCanceler.addPending(config2)

      requestCanceler.cancelByUrl('/users')

      // config1 已被取消并从 Map 移除；config2 仍在 Map 中
      // 后续 cancelAll 应取消 config2（证明它还在 Map 中）
      requestCanceler.cancelAll()
      expect(config1.signal!.aborted).toBe(true)
      expect(config2.signal!.aborted).toBe(true)
    })

    it('无匹配时不影响任何请求', () => {
      const config1: AxiosRequestConfig = { method: 'GET', url: '/users/me' }
      requestCanceler.addPending(config1)

      requestCanceler.cancelByUrl('/non-existent')

      expect(config1.signal!.aborted).toBe(false)
    })
  })

  describe('skipCancel', () => {
    it('skipCancel: true 的请求不纳入取消管理（不写入 signal）', () => {
      const config: AxiosRequestConfig = {
        method: 'GET',
        url: '/users/me',
        skipCancel: true,
      }

      requestCanceler.addPending(config)

      // signal 未被写入（保持 undefined）
      expect(config.signal).toBeUndefined()
    })

    it('skipCancel: true 的请求不受 cancelAll 影响', () => {
      // 模拟外部已设置 signal 的场景（如轮询请求自带 controller）
      const controller = new AbortController()
      const config: AxiosRequestConfig = {
        method: 'GET',
        url: '/users/me',
        skipCancel: true,
        signal: controller.signal,
      }

      requestCanceler.addPending(config)
      requestCanceler.cancelAll()

      // skipCancel 请求的 signal 不受 cancelAll 影响
      expect(controller.signal.aborted).toBe(false)
    })

    it('skipCancel: true 的请求 removePending 不报错', () => {
      const config: AxiosRequestConfig = {
        method: 'GET',
        url: '/users/me',
        skipCancel: true,
      }

      requestCanceler.addPending(config)

      expect(() => requestCanceler.removePending(config)).not.toThrow()
    })

    it('skipCancel: true 的请求不受 cancelByUrl 影响', () => {
      const controller = new AbortController()
      const config: AxiosRequestConfig = {
        method: 'GET',
        url: '/users/me',
        skipCancel: true,
        signal: controller.signal,
      }

      requestCanceler.addPending(config)
      requestCanceler.cancelByUrl('/users')

      expect(controller.signal.aborted).toBe(false)
    })
  })
})
