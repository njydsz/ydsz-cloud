import { describe, it, expect } from 'vitest'
import { formatMoney, maskPhone, maskIdCard } from '@/utils/format'

describe('format utils', () => {
  describe('formatMoney', () => {
    it('should format number with default prefix and decimals', () => {
      expect(formatMoney(1234.5)).toBe('¥1,234.50')
    })

    it('should return "-" for null/undefined', () => {
      expect(formatMoney(null)).toBe('-')
      expect(formatMoney(undefined)).toBe('-')
    })

    it('should support custom prefix', () => {
      expect(formatMoney(100, '$')).toBe('$100.00')
    })

    it('should support custom decimals', () => {
      expect(formatMoney(100, '¥', 0)).toBe('¥100')
    })
  })

  describe('maskPhone', () => {
    it('should mask middle 4 digits', () => {
      expect(maskPhone('13812345678')).toBe('138****5678')
    })

    it('should return "-" for null', () => {
      expect(maskPhone(null)).toBe('-')
      expect(maskPhone(undefined)).toBe('-')
    })
  })

  describe('maskIdCard', () => {
    it('should mask middle digits', () => {
      expect(maskIdCard('320123199001011234')).toBe('3201***********1234')
    })

    it('should return "-" for null', () => {
      expect(maskIdCard(null)).toBe('-')
    })
  })
})
