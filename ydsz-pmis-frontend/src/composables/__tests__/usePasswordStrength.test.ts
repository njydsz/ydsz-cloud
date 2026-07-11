import { describe, it, expect, vi } from 'vitest'
import { ref } from 'vue'
import { calcPasswordStrength, usePasswordStrength } from '../usePasswordStrength'

// Mock i18n to avoid importing full locale setup
vi.mock('@/locales', () => ({
  default: {
    global: {
      t: (key: string) => key, // Return the key as-is for testing
    },
  },
}))

describe('composables/usePasswordStrength', () => {
  describe('calcPasswordStrength', () => {
    it('should return score 0 for null', () => {
      const result = calcPasswordStrength(null)
      expect(result.score).toBe(0)
      expect(result.level).toBe('WEAKEST')
      expect(result.percent).toBe(10)
    })

    it('should return score 0 for empty string', () => {
      const result = calcPasswordStrength('')
      expect(result.score).toBe(0)
    })

    it('should return score 1 for 8-char lowercase only', () => {
      const result = calcPasswordStrength('abcdefgh')
      expect(result.score).toBe(1)
    })

    it('should return score 2 for 8-char mixed case', () => {
      const result = calcPasswordStrength('Abcdefgh')
      expect(result.score).toBe(2)
    })

    it('should return score 3 for 12-char mixed case + digits', () => {
      const result = calcPasswordStrength('Abcdefghijkl1')
      expect(result.score).toBe(3)
    })

    it('should return score 4 for 12-char mixed case + digits + special', () => {
      const result = calcPasswordStrength('Abcdefghij1!')
      expect(result.score).toBe(4)
      expect(result.level).toBe('STRONGEST')
      expect(result.percent).toBe(100)
    })

    it('should cap score at 4', () => {
      const result = calcPasswordStrength('Abcdefghijklmnop123!@#')
      expect(result.score).toBe(4)
    })

    it('should include rules array with 4 items', () => {
      const result = calcPasswordStrength('Ab1!')
      expect(result.rules).toHaveLength(4)
      expect(result.rules[0].pass).toBe(true) // uppercase
      expect(result.rules[1].pass).toBe(true) // lowercase
      expect(result.rules[2].pass).toBe(true) // digit
      expect(result.rules[3].pass).toBe(true) // special
    })

    it('should include suggestions for weak passwords', () => {
      const result = calcPasswordStrength('a')
      expect(result.suggestions.length).toBeGreaterThan(0)
    })

    it('should have empty suggestions for strong passwords', () => {
      const result = calcPasswordStrength('Abcdefghij1!')
      expect(result.suggestions).toHaveLength(0)
    })

    it('should return correct color for each level', () => {
      expect(calcPasswordStrength('').color).toBe('#f56c6c')     // WEAKEST
      expect(calcPasswordStrength('abcdefgh').color).toBe('#e6a23c') // WEAK
      expect(calcPasswordStrength('Abcdefgh').color).toBe('#f0c40c') // MEDIUM
      expect(calcPasswordStrength('Abcdefghijkl1').color).toBe('#409eff') // STRONG
      expect(calcPasswordStrength('Abcdefghij1!').color).toBe('#67c23a') // STRONGEST
    })
  })

  describe('usePasswordStrength', () => {
    it('should return reactive result', () => {
      const password = ref<string | null>(null)
      const { result } = usePasswordStrength(password)
      expect(result.value.score).toBe(0)

      password.value = 'Abcdefghij1!'
      expect(result.value.score).toBe(4)
    })

    it('should update when password changes', () => {
      const password = ref('weak')
      const { result } = usePasswordStrength(password)
      expect(result.value.score).toBe(0)

      password.value = 'Abcdefghij1!'
      expect(result.value.score).toBe(4)
    })
  })
})
