/**
 * @file request.test.ts
 * @description 测试 request 工具模块的导出
 * @vitest-environment jsdom
 */
import { describe, it, expect } from 'vitest'
import { request } from '@/utils/request'

describe('request.ts - 请求封装', () => {
  it('request 应为函数', () => {
    expect(request).toBeDefined()
    expect(typeof request).toBe('function')
  })
})