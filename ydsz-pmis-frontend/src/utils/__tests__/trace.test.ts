/**
 * @file 链路追踪 ID 生成单元测试
 * @covers generateTraceId
 */
import { describe, it, expect } from 'vitest'
import { generateTraceId } from '../trace'

describe('trace utils', () => {
  describe('generateTraceId', () => {
    it('should return a string', () => {
      const id = generateTraceId()
      expect(typeof id).toBe('string')
    })

    it('should contain a hyphen separator', () => {
      const id = generateTraceId()
      expect(id).toContain('-')
    })

    it('should have format: timestamp-random', () => {
      const id = generateTraceId()
      const parts = id.split('-')
      expect(parts).toHaveLength(2)
      // timestamp part should be base36
      expect(parts[0]).toMatch(/^[0-9a-z]+$/)
      // random part should be 8 chars
      expect(parts[1]).toHaveLength(8)
    })

    it('should generate unique IDs in rapid succession', () => {
      const ids = new Set<string>()
      for (let i = 0; i < 100; i++) {
        ids.add(generateTraceId())
      }
      expect(ids.size).toBe(100)
    })

    it('should not be empty', () => {
      const id = generateTraceId()
      expect(id.length).toBeGreaterThan(5)
    })
  })
})
