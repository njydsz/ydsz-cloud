/**
 * @file storage.test.ts
 * @description 测试 localStorage 安全访问工具 safeGet/safeSet/safeRemove/clearAllPersisted
 * @vitest-environment jsdom
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { safeGet, safeSet, safeRemove, clearAllPersisted } from '@/utils/storage'

describe('storage.ts - localStorage 安全访问', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('safeGet / safeSet / safeRemove 基本功能', () => {
    it('safeSet 写入后 safeGet 应能读取到对应值', () => {
      const ok = safeSet('pmis:test:key', 'hello-world')
      expect(ok).toBe(true)
      expect(safeGet('pmis:test:key')).toBe('hello-world')
    })

    it('safeGet 在 key 不存在时应返回 null', () => {
      expect(safeGet('not-exist-key')).toBeNull()
    })

    it('safeRemove 应删除指定 key', () => {
      safeSet('pmis:test:remove', 'value')
      expect(safeGet('pmis:test:remove')).toBe('value')

      safeRemove('pmis:test:remove')
      expect(safeGet('pmis:test:remove')).toBeNull()
    })

    it('safeRemove 删除不存在的 key 时不应抛异常', () => {
      expect(() => safeRemove('never-existed')).not.toThrow()
    })

    it('safeSet 支持写入 JSON 字符串并可通过 safeGet 读取解析', () => {
      const obj = { token: 'abc', permissions: ['system:user:list'] }
      safeSet('pmis:test:obj', JSON.stringify(obj))
      expect(JSON.parse(safeGet('pmis:test:obj') || '{}')).toEqual(obj)
    })
  })

  describe('localStorage 不可用场景（隐私模式 / 存储被禁用）', () => {
    // 保存原始 localStorage 引用，每个用例结束后恢复
    const originalStorage = window.localStorage

    afterEach(() => {
      // 用数据属性恢复，覆盖用例中定义的 getter
      Object.defineProperty(window, 'localStorage', {
        value: originalStorage,
        writable: true,
        configurable: true,
      })
    })

    function makeStorageUnavailable(): void {
      Object.defineProperty(window, 'localStorage', {
        get: () => {
          throw new Error('localStorage unavailable')
        },
        configurable: true,
      })
    }

    it('localStorage 访问抛异常时 safeGet 不应抛出且返回 null', () => {
      makeStorageUnavailable()
      expect(() => safeGet('any-key')).not.toThrow()
      expect(safeGet('any-key')).toBeNull()
    })

    it('localStorage 访问抛异常时 safeSet 不应抛出且返回 false', () => {
      makeStorageUnavailable()
      expect(() => safeSet('any-key', 'val')).not.toThrow()
      expect(safeSet('any-key', 'val')).toBe(false)
    })

    it('localStorage 访问抛异常时 safeRemove 不应抛出', () => {
      makeStorageUnavailable()
      expect(() => safeRemove('any-key')).not.toThrow()
    })

    it('localStorage 访问抛异常时 clearAllPersisted 不应抛出', () => {
      makeStorageUnavailable()
      expect(() => clearAllPersisted()).not.toThrow()
    })
  })

  describe('clearAllPersisted', () => {
    it('应清除所有 pmis: 前缀的 key', () => {
      localStorage.setItem('pmis:user:v1', 'data1')
      localStorage.setItem('pmis:permission:v1', 'data2')
      localStorage.setItem('pmis:app:v1', 'data3')

      clearAllPersisted()

      expect(localStorage.getItem('pmis:user:v1')).toBeNull()
      expect(localStorage.getItem('pmis:permission:v1')).toBeNull()
      expect(localStorage.getItem('pmis:app:v1')).toBeNull()
    })

    it('不应清除非 pmis: 前缀的 key', () => {
      localStorage.setItem('pmis:user:v1', 'persisted')
      // 历史下划线 key（auth.ts 写入），前缀为 pmis_ 而非 pmis:
      localStorage.setItem('pmis_token', 'legacy-token')
      // 历史业务 key（无前缀）
      localStorage.setItem('userInfo', '{}')
      // 其他业务 key
      localStorage.setItem('other-key', 'other')

      clearAllPersisted()

      // pmis: 前缀应被清除
      expect(localStorage.getItem('pmis:user:v1')).toBeNull()
      // 非 pmis: 前缀的 key 应保留
      expect(localStorage.getItem('pmis_token')).toBe('legacy-token')
      expect(localStorage.getItem('userInfo')).toBe('{}')
      expect(localStorage.getItem('other-key')).toBe('other')
    })

    it('无 pmis: 前缀 key 时不应影响其他数据', () => {
      localStorage.setItem('other-key', 'keep')
      clearAllPersisted()
      expect(localStorage.getItem('other-key')).toBe('keep')
    })
  })
})
