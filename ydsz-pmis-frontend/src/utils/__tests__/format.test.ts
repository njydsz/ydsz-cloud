import { describe, it, expect } from 'vitest'
import {
  formatDate,
  formatMoney,
  formatNumber,
  formatFileSize,
  maskPhone,
  maskIdCard,
  maskEmail,
  maskBankCard,
  maskName,
  mask,
} from '../format'

describe('utils/format', () => {
  describe('formatDate', () => {
    it('should format a valid date string', () => {
      const result = formatDate('2024-01-15 10:30:00')
      expect(result).toBe('2024-01-15 10:30:00')
    })

    it('should return "-" for null', () => {
      expect(formatDate(null)).toBe('-')
    })

    it('should return "-" for undefined', () => {
      expect(formatDate(undefined)).toBe('-')
    })

    it('should support custom format', () => {
      const result = formatDate('2024-01-15', 'YYYY/MM/DD')
      expect(result).toBe('2024/01/15')
    })
  })

  describe('formatMoney', () => {
    it('should format with default prefix and decimals', () => {
      expect(formatMoney(1234.56)).toBe('¥1,234.56')
    })

    it('should format with custom prefix', () => {
      expect(formatMoney(1234.56, '$')).toBe('$1,234.56')
    })

    it('should format with custom decimals', () => {
      expect(formatMoney(1234.5678, '¥', 3)).toBe('¥1,234.568')
    })

    it('should return "-" for null', () => {
      expect(formatMoney(null)).toBe('-')
    })

    it('should return "-" for undefined', () => {
      expect(formatMoney(undefined)).toBe('-')
    })

    it('should format large numbers with thousands separator', () => {
      expect(formatMoney(1234567.89)).toBe('¥1,234,567.89')
    })
  })

  describe('formatNumber', () => {
    it('should format with default 0 decimals', () => {
      expect(formatNumber(1234)).toBe('1,234')
    })

    it('should format with custom decimals', () => {
      expect(formatNumber(1234.5, 2)).toBe('1,234.50')
    })

    it('should return "-" for null', () => {
      expect(formatNumber(null)).toBe('-')
    })

    it('should format large numbers', () => {
      expect(formatNumber(1000000)).toBe('1,000,000')
    })
  })

  describe('formatFileSize', () => {
    it('should format 0 bytes', () => {
      expect(formatFileSize(0)).toBe('0 B')
    })

    it('should format bytes', () => {
      expect(formatFileSize(512)).toBe('512.00 B')
    })

    it('should format KB', () => {
      expect(formatFileSize(1536)).toBe('1.50 KB')
    })

    it('should format MB', () => {
      expect(formatFileSize(1048576)).toBe('1.00 MB')
    })

    it('should format GB', () => {
      expect(formatFileSize(1073741824)).toBe('1.00 GB')
    })
  })

  describe('maskPhone', () => {
    it('should mask phone number', () => {
      expect(maskPhone('13812345678')).toBe('138****5678')
    })

    it('should return "-" for null', () => {
      expect(maskPhone(null)).toBe('-')
    })

    it('should return "-" for undefined', () => {
      expect(maskPhone(undefined)).toBe('-')
    })
  })

  describe('maskIdCard', () => {
    it('should mask ID card', () => {
      expect(maskIdCard('110101199001011234')).toBe('1101***********1234')
    })

    it('should return "-" for null', () => {
      expect(maskIdCard(null)).toBe('-')
    })
  })

  describe('maskEmail', () => {
    it('should mask email', () => {
      expect(maskEmail('zhangsan@example.com')).toBe('z***@example.com')
    })

    it('should return original for short email', () => {
      expect(maskEmail('a@b.com')).toBe('a@b.com')
    })

    it('should return "-" for null', () => {
      expect(maskEmail(null)).toBe('-')
    })
  })

  describe('maskBankCard', () => {
    it('should mask bank card', () => {
      expect(maskBankCard('6228480000001234')).toBe('6228 **** **** 1234')
    })

    it('should handle card with spaces', () => {
      expect(maskBankCard('6228 4800 0000 1234')).toBe('6228 **** **** 1234')
    })

    it('should return **** for short card', () => {
      expect(maskBankCard('1234')).toBe('****')
    })

    it('should return "-" for null', () => {
      expect(maskBankCard(null)).toBe('-')
    })
  })

  describe('maskName', () => {
    it('should mask Chinese name', () => {
      expect(maskName('张三丰')).toBe('张**')
    })

    it('should mask two-char name', () => {
      expect(maskName('张三')).toBe('张*')
    })

    it('should mask single char name', () => {
      expect(maskName('张')).toBe('*')
    })

    it('should return "-" for null', () => {
      expect(maskName(null)).toBe('-')
    })
  })

  describe('mask (universal)', () => {
    it('should mask phone via type', () => {
      expect(mask('13812345678', 'phone')).toBe('138****5678')
    })

    it('should mask email via type', () => {
      expect(mask('test@example.com', 'email')).toBe('t***@example.com')
    })

    it('should mask idCard via type', () => {
      expect(mask('110101199001011234', 'idCard')).toContain('1101')
      expect(mask('110101199001011234', 'idCard')).toContain('1234')
    })

    it('should mask bankCard via type', () => {
      expect(mask('6228480000001234', 'bankCard')).toContain('6228')
      expect(mask('6228480000001234', 'bankCard')).toContain('1234')
    })

    it('should mask name via type', () => {
      expect(mask('张三丰', 'name')).toBe('张**')
    })
  })
})
