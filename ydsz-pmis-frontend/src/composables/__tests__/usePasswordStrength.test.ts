/**
 * @file usePasswordStrength.test.ts
 * @description 测试密码强度计算 composable
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi } from 'vitest'
import { ref, nextTick } from 'vue'

// Mock @/locales，避免完整 i18n 初始化
vi.mock('@/locales', () => ({
  default: {
    global: {
      t: (key: string) => key,
    },
  },
  setLocale: vi.fn(),
  getLocale: vi.fn(() => 'zh-CN'),
}))

import {
  calcPasswordStrength,
  usePasswordStrength,
} from '@/composables/usePasswordStrength'

describe('calcPasswordStrength', () => {
  describe('弱密码', () => {
    it('"123" 应返回 score 0-1（仅含数字）', () => {
      const result = calcPasswordStrength('123')
      expect(result.score).toBeGreaterThanOrEqual(0)
      expect(result.score).toBeLessThanOrEqual(1)
      // 仅数字 → +1 分
      expect(result.score).toBe(1)
      expect(result.level).toBe('WEAK')
    })

    it('"123" 应有数字规则通过、其余不通过', () => {
      const result = calcPasswordStrength('123')
      expect(result.rules[0].pass).toBe(false) // 大写
      expect(result.rules[1].pass).toBe(false) // 小写
      expect(result.rules[2].pass).toBe(true) // 数字
      expect(result.rules[3].pass).toBe(false) // 特殊字符
    })
  })

  describe('中等密码', () => {
    it('"Abc123" 应返回 score 2-3（大小写+数字）', () => {
      const result = calcPasswordStrength('Abc123')
      expect(result.score).toBeGreaterThanOrEqual(2)
      expect(result.score).toBeLessThanOrEqual(3)
      // 长度 < 8（0）+ 大小写（+1）+ 数字（+1）= 2
      expect(result.score).toBe(2)
      expect(result.level).toBe('MEDIUM')
    })
  })

  describe('强密码', () => {
    it('"Abc123!@#xyz" 应返回 score 4（满足全部规则）', () => {
      const result = calcPasswordStrength('Abc123!@#xyz')
      expect(result.score).toBe(4)
      expect(result.level).toBe('STRONGEST')
      expect(result.percent).toBe(100)
    })

    it('强密码所有规则均通过', () => {
      const result = calcPasswordStrength('Abc123!@#xyz')
      expect(result.rules.every((r) => r.pass)).toBe(true)
    })
  })

  describe('空字符串', () => {
    it('空字符串应返回 score 0', () => {
      const result = calcPasswordStrength('')
      expect(result.score).toBe(0)
      expect(result.level).toBe('WEAKEST')
      expect(result.percent).toBe(10)
    })

    it('null 应返回 score 0', () => {
      const result = calcPasswordStrength(null)
      expect(result.score).toBe(0)
    })

    it('undefined 应返回 score 0', () => {
      const result = calcPasswordStrength(undefined)
      expect(result.score).toBe(0)
    })
  })

  describe('改进建议', () => {
    it('空密码应生成多条改进建议', () => {
      const result = calcPasswordStrength('')
      expect(result.suggestions.length).toBeGreaterThan(0)
      // 应包含 min8 / addUpper / addLower / addDigit / addSpecial
      expect(result.suggestions.length).toBe(5)
    })

    it('"123" 应建议增加大小写字母和特殊字符', () => {
      const result = calcPasswordStrength('123')
      // 长度 < 8（min8）+ 无大写 + 无小写 + 无特殊 = 4 条建议
      expect(result.suggestions.length).toBe(4)
    })

    it('"Abc123" 应建议增加特殊字符和最小长度', () => {
      const result = calcPasswordStrength('Abc123')
      // 长度 < 8（min8）+ 无特殊 = 2 条建议
      expect(result.suggestions.length).toBe(2)
    })

    it('强密码不应有改进建议', () => {
      const result = calcPasswordStrength('Abc123!@#xyz')
      expect(result.suggestions.length).toBe(0)
    })
  })

  describe('展示属性', () => {
    it('应返回正确的颜色', () => {
      expect(calcPasswordStrength('').color).toBe('#f56c6c') // 0 - 红
      expect(calcPasswordStrength('123').color).toBe('#e6a23c') // 1 - 橙
      expect(calcPasswordStrength('Abc123').color).toBe('#f0c40c') // 2 - 黄
      expect(calcPasswordStrength('Abc123!@#xyz').color).toBe('#67c23a') // 4 - 绿
    })

    it('应返回 percent 展示值', () => {
      expect(calcPasswordStrength('').percent).toBe(10)
      expect(calcPasswordStrength('Abc123!@#xyz').percent).toBe(100)
    })
  })
})

describe('usePasswordStrength', () => {
  it('应返回响应式 result', async () => {
    const password = ref('')
    const { result } = usePasswordStrength(password)

    expect(result.value.score).toBe(0)

    // 修改密码，验证响应式更新
    password.value = 'Abc123!@#xyz'
    await nextTick()
    expect(result.value.score).toBe(4)
    expect(result.value.level).toBe('STRONGEST')
  })

  it('应跟踪 password ref 变化', async () => {
    const password = ref<string | null>('123')
    const { result } = usePasswordStrength(password)

    expect(result.value.score).toBe(1)

    password.value = 'Abc123'
    await nextTick()
    expect(result.value.score).toBe(2)

    password.value = 'Abc123!@#xyz'
    await nextTick()
    expect(result.value.score).toBe(4)
  })

  it('password 为 null 时应返回 score 0', () => {
    const password = ref<string | null>(null)
    const { result } = usePasswordStrength(password)
    expect(result.value.score).toBe(0)
    expect(result.value.level).toBe('WEAKEST')
  })
})
