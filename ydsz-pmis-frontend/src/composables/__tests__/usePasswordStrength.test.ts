/**
 * @file usePasswordStrength composable 单元测试
 * @description 测试密码强度检测逻辑
 * @module composables/__tests__/usePasswordStrength
 */
import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { usePasswordStrength, calcPasswordStrength } from '../usePasswordStrength'

// Mock i18n
vi.mock('@/locales', () => ({
  default: {
    global: {
      t: (key: string) => key,
    },
  },
}))

describe('calcPasswordStrength', () => {
  it('空密码强度为 0 (WEAKEST)', () => {
    const result = calcPasswordStrength('')
    expect(result.score).toBe(0)
    expect(result.level).toBe('WEAKEST')
  })

  it('短密码强度为弱', () => {
    const result = calcPasswordStrength('abc')
    expect(result.score).toBeLessThanOrEqual(1)
  })

  it('包含大小写+数字+特殊字符的长密码为最强', () => {
    const result = calcPasswordStrength('Abc@1234xyz!')
    expect(result.score).toBe(4)
    expect(result.level).toBe('STRONGEST')
    expect(result.percent).toBe(100)
  })

  it('中等复杂度密码为中或良', () => {
    const result = calcPasswordStrength('abcdef12345')
    expect(result.score).toBeGreaterThanOrEqual(2)
    expect(result.score).toBeLessThanOrEqual(3)
  })

  it('仅大小写字母（长度>=8）得分为 2', () => {
    const result = calcPasswordStrength('Abcdefghij')
    expect(result.score).toBe(2) // length>=8 + upper&lower
  })

  it('仅数字（长度>=12）得分为 3', () => {
    const result = calcPasswordStrength('123456789012')
    expect(result.score).toBe(3) // length>=8 + length>=12 + digit
  })

  it('suggestions 包含未通过规则的提示', () => {
    const result = calcPasswordStrength('abc')
    expect(result.suggestions.length).toBeGreaterThan(0)
  })

  it('rules 数组包含 4 条规则检查结果', () => {
    const result = calcPasswordStrength('Test123!')
    expect(result.rules).toHaveLength(4)
    expect(result.rules[0].pass).toBe(true) // uppercase
    expect(result.rules[1].pass).toBe(true) // lowercase
    expect(result.rules[2].pass).toBe(true) // digit
    expect(result.rules[3].pass).toBe(true) // special
  })

  it('null/undefined 密码视为空', () => {
    expect(calcPasswordStrength(null).score).toBe(0)
    expect(calcPasswordStrength(undefined).score).toBe(0)
  })
})

describe('usePasswordStrength (composable)', () => {
  it('返回响应式结果', () => {
    const password = ref('')
    const { result } = usePasswordStrength(password)
    expect(result.value.score).toBe(0)

    password.value = 'Abc@1234xyz!'
    expect(result.value.score).toBe(4)
    expect(result.value.level).toBe('STRONGEST')
  })
})
