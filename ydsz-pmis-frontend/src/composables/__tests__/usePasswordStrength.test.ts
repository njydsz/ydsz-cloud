/**
 * @file usePasswordStrength 单元测试
 * @description 验证密码强度计算函数 calcPasswordStrength 的评分规则、等级映射、规则明细、
 *              建议项生成及 percent / color 映射，覆盖空值、单类、多类组合等边界场景。
 * @module composables/__tests__/usePasswordStrength
 */
import { describe, it, expect } from 'vitest'
import { calcPasswordStrength } from '@/composables/usePasswordStrength'

describe('usePasswordStrength 密码强度计算', () => {
  it('空值应得 0 分（极弱）', () => {
    const r = calcPasswordStrength('')
    expect(r.score).toBe(0)
    expect(r.level).toBe('WEAKEST')
    expect(r.text).toBe('极弱')
  })

  it('null/undefined 视为空', () => {
    expect(calcPasswordStrength(null).score).toBe(0)
    expect(calcPasswordStrength(undefined).score).toBe(0)
  })

  it('仅 1 位应得 0 分', () => {
    expect(calcPasswordStrength('a').score).toBe(0)
  })

  it('长度 8 含 1 类得 1 分（弱）', () => {
    const r = calcPasswordStrength('aaaaaaaa')
    // 长度 >= 8 +1, 长度 >= 12? no, 同时大小写? no
    // 仅长度一项
    expect(r.score).toBe(1)
    expect(r.level).toBe('WEAK')
    expect(r.text).toBe('弱')
  })

  it('长度 8 + 大小写 + 数字 + 特殊 = 4 分（强）', () => {
    const r = calcPasswordStrength('Aa1!aaaa')
    // 长度 >=8: +1
    // 长度 >=12: 0
    // 大小写: +1
    // 数字: +1
    // 特殊: +1
    expect(r.score).toBe(4)
    expect(r.level).toBe('STRONGEST')
    expect(r.text).toBe('强')
  })

  it('长度 12 仅小写 = 2 分（中）', () => {
    const r = calcPasswordStrength('aaaaaaaaaaaa')
    // 长度 >= 8 +1
    // 长度 >= 12 +1
    // 大小写? no
    // 数字? no
    // 特殊? no
    expect(r.score).toBe(2)
    expect(r.level).toBe('MEDIUM')
  })

  it('长度 8 仅大写 = 1 分（弱）', () => {
    const r = calcPasswordStrength('AAAAAAAA')
    expect(r.score).toBe(1)
  })

  it('长度 8 大小写 + 数字 = 3 分（良）', () => {
    const r = calcPasswordStrength('Aaaaaa11')
    // 长度>=8: +1
    // 大小写: +1
    // 数字: +1
    // 长度>=12: no
    // 特殊: no
    expect(r.score).toBe(3)
    expect(r.level).toBe('STRONG')
  })

  it('分数上限 4', () => {
    const r = calcPasswordStrength('Aaaaaaaaaa1111!@#$%')
    expect(r.score).toBeLessThanOrEqual(4)
  })

  it('规则明细正确', () => {
    const r = calcPasswordStrength('Aaaaaa1!')
    expect(r.rules).toHaveLength(4)
    expect(r.rules[0]).toEqual({ label: '大写字母', pass: true })
    expect(r.rules[1]).toEqual({ label: '小写字母', pass: true })
    expect(r.rules[2]).toEqual({ label: '数字', pass: true })
    expect(r.rules[3]).toEqual({ label: '特殊字符', pass: true })
  })

  it('建议项：长度不足', () => {
    const r = calcPasswordStrength('aA1!')
    expect(r.suggestions.some((s) => s.includes('8 位'))).toBe(true)
  })

  it('建议项：未含大写', () => {
    const r = calcPasswordStrength('aaaaaaaa1!')
    expect(r.suggestions.some((s) => s.includes('大写'))).toBe(true)
  })

  it('建议项：未含小写', () => {
    const r = calcPasswordStrength('AAAAAAAA1!')
    expect(r.suggestions.some((s) => s.includes('小写'))).toBe(true)
  })

  it('建议项：未含数字', () => {
    const r = calcPasswordStrength('Aaaaaaaa!')
    expect(r.suggestions.some((s) => s.includes('数字'))).toBe(true)
  })

  it('建议项：未含特殊字符', () => {
    const r = calcPasswordStrength('Aaaaaaaa1')
    expect(r.suggestions.some((s) => s.includes('特殊'))).toBe(true)
  })

  it('满分时建议应为空', () => {
    // 12 位 + 大小写 + 数字 + 特殊 → 全规则命中
    const r = calcPasswordStrength('Aa1!aaaa@@bb')
    expect(r.score).toBe(4)
    expect(r.suggestions.length).toBe(0)
  })

  it('percent 与 score 对应', () => {
    expect(calcPasswordStrength('').percent).toBe(10)
    expect(calcPasswordStrength('aaaaaaaa').percent).toBe(30)
    expect(calcPasswordStrength('aaaaaaaaaaaa').percent).toBe(55)
    expect(calcPasswordStrength('Aaaaaa11').percent).toBe(80)
    expect(calcPasswordStrength('Aa1!aaaa').percent).toBe(100)
  })

  it('color 与 score 对应', () => {
    expect(calcPasswordStrength('').color).toBe('#f56c6c')
    expect(calcPasswordStrength('Aa1!aaaa').color).toBe('#67c23a')
  })
})
