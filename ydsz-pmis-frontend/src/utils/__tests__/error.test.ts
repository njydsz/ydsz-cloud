/**
 * @file 错误处理工具单元测试
 * @covers SysException / HttpException / isHandledError
 */
import { describe, it, expect } from 'vitest'
import { SysException, HttpException, isHandledError } from '../error'

describe('error utils', () => {
  describe('SysException', () => {
    it('should create with message and code', () => {
      const err = new SysException('test error', 1001)
      expect(err.message).toBe('test error')
      expect(err.code).toBe(1001)
      expect(err.name).toBe('SysException')
      expect(err.handled).toBe(true) // default
    })

    it('should create with handled=false', () => {
      const err = new SysException('unhandled', 1002, false)
      expect(err.handled).toBe(false)
    })

    it('should be an instance of Error', () => {
      const err = new SysException('test', 1001)
      expect(err).toBeInstanceOf(Error)
    })
  })

  describe('HttpException', () => {
    it('should create with message and status', () => {
      const err = new HttpException('not found', 404)
      expect(err.message).toBe('not found')
      expect(err.status).toBe(404)
      expect(err.name).toBe('HttpException')
      expect(err.handled).toBe(true) // default
    })

    it('should create with handled=false', () => {
      const err = new HttpException('unhandled', 500, false)
      expect(err.handled).toBe(false)
    })

    it('should be an instance of Error', () => {
      const err = new HttpException('test', 500)
      expect(err).toBeInstanceOf(Error)
    })
  })

  describe('isHandledError', () => {
    it('should return true for handled SysException', () => {
      const err = new SysException('test', 1001, true)
      expect(isHandledError(err)).toBe(true)
    })

    it('should return false for unhandled SysException', () => {
      const err = new SysException('test', 1001, false)
      expect(isHandledError(err)).toBe(false)
    })

    it('should return true for handled HttpException', () => {
      const err = new HttpException('test', 500, true)
      expect(isHandledError(err)).toBe(true)
    })

    it('should return false for unhandled HttpException', () => {
      const err = new HttpException('test', 500, false)
      expect(isHandledError(err)).toBe(false)
    })

    it('should return false for regular Error', () => {
      const err = new Error('regular error')
      expect(isHandledError(err)).toBe(false)
    })

    it('should return false for non-error values', () => {
      expect(isHandledError(null)).toBe(false)
      expect(isHandledError(undefined)).toBe(false)
      expect(isHandledError('string')).toBe(false)
      expect(isHandledError(42)).toBe(false)
    })
  })
})
