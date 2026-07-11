import { describe, it, expect, beforeEach, vi } from 'vitest'
import { safeGet, safeSet, safeRemove, clearAllPersisted } from '../storage'

describe('utils/storage', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('safeGet', () => {
    it('should return value when key exists', () => {
      localStorage.setItem('test-key', 'test-value')
      expect(safeGet('test-key')).toBe('test-value')
    })

    it('should return null when key does not exist', () => {
      expect(safeGet('non-existent')).toBeNull()
    })

    it('should return null when localStorage throws', () => {
      const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('Storage disabled')
      })
      expect(safeGet('any-key')).toBeNull()
      spy.mockRestore()
    })
  })

  describe('safeSet', () => {
    it('should set value successfully', () => {
      expect(safeSet('test-key', 'test-value')).toBe(true)
      expect(localStorage.getItem('test-key')).toBe('test-value')
    })

    it('should return false when localStorage throws', () => {
      const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('Quota exceeded')
      })
      expect(safeSet('test-key', 'test-value')).toBe(false)
      spy.mockRestore()
    })
  })

  describe('safeRemove', () => {
    it('should remove key successfully', () => {
      localStorage.setItem('test-key', 'test-value')
      safeRemove('test-key')
      expect(localStorage.getItem('test-key')).toBeNull()
    })

    it('should not throw when key does not exist', () => {
      expect(() => safeRemove('non-existent')).not.toThrow()
    })

    it('should not throw when localStorage throws', () => {
      const spy = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
        throw new Error('Storage disabled')
      })
      expect(() => safeRemove('any-key')).not.toThrow()
      spy.mockRestore()
    })
  })

  describe('clearAllPersisted', () => {
    it('should remove only pmis: prefixed keys', () => {
      localStorage.setItem('pmis:user', '{"name":"test"}')
      localStorage.setItem('pmis:settings', '{"theme":"dark"}')
      localStorage.setItem('other-key', 'should-keep')
      localStorage.setItem('pmis_token', 'should-keep')

      clearAllPersisted()

      expect(localStorage.getItem('pmis:user')).toBeNull()
      expect(localStorage.getItem('pmis:settings')).toBeNull()
      expect(localStorage.getItem('other-key')).toBe('should-keep')
      expect(localStorage.getItem('pmis_token')).toBe('should-keep')
    })

    it('should not throw when localStorage is empty', () => {
      expect(() => clearAllPersisted()).not.toThrow()
    })

    it('should not throw when localStorage throws', () => {
      const spy = vi.spyOn(Storage.prototype, 'key').mockImplementation(() => {
        throw new Error('Storage disabled')
      })
      expect(() => clearAllPersisted()).not.toThrow()
      spy.mockRestore()
    })
  })
})
