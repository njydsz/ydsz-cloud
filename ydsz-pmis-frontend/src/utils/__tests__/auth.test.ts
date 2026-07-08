/**
 * @file Token 持久化工具单元测试
 * @covers getToken / setToken / getRefreshToken / removeToken
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { getToken, setToken, getRefreshToken, removeToken } from '../auth'

describe('auth utils', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  describe('getToken', () => {
    it('should return null when no token is set', () => {
      expect(getToken()).toBeNull()
    })

    it('should return the stored token', () => {
      localStorage.setItem('pmis_token', 'test-access-token')
      expect(getToken()).toBe('test-access-token')
    })
  })

  describe('setToken', () => {
    it('should store access token', () => {
      setToken('my-access-token')
      expect(localStorage.getItem('pmis_token')).toBe('my-access-token')
    })

    it('should store both access and refresh token', () => {
      setToken('access-123', 'refresh-456')
      expect(localStorage.getItem('pmis_token')).toBe('access-123')
      expect(localStorage.getItem('pmis_refresh_token')).toBe('refresh-456')
    })

    it('should not overwrite refresh token when not provided', () => {
      localStorage.setItem('pmis_refresh_token', 'existing-refresh')
      setToken('new-access')
      expect(localStorage.getItem('pmis_refresh_token')).toBe('existing-refresh')
    })
  })

  describe('getRefreshToken', () => {
    it('should return null when no refresh token is set', () => {
      expect(getRefreshToken()).toBeNull()
    })

    it('should return the stored refresh token', () => {
      localStorage.setItem('pmis_refresh_token', 'my-refresh')
      expect(getRefreshToken()).toBe('my-refresh')
    })
  })

  describe('removeToken', () => {
    it('should remove both tokens', () => {
      setToken('access', 'refresh')
      removeToken()
      expect(getToken()).toBeNull()
      expect(getRefreshToken()).toBeNull()
    })

    it('should be safe to call when no tokens exist', () => {
      expect(() => removeToken()).not.toThrow()
    })
  })
})
