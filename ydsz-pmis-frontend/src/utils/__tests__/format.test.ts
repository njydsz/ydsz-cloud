/**
 * @file format.test.ts
 * @description 测试通用格式化工具函数
 * @vitest-environment jsdom
 */
import { describe, it, expect } from 'vitest'
import {
  formatDate,
  formatMoney,
  formatNumber,
  formatFileSize,
  maskPhone,
  maskIdCard,
} from '@/utils/format'

describe('formatDate', () => {
  it('应格式化日期字符串为默认格式 YYYY-MM-DD HH:mm:ss', () => {
    const result = formatDate('2024-01-15 10:30:00')
    expect(result).toBe('2024-01-15 10:30:00')
  })

  it('应支持自定义格式', () => {
    const result = formatDate('2024-01-15', 'YYYY/MM/DD')
    expect(result).toBe('2024/01/15')
  })

  it('应格式化 Date 对象', () => {
    const date = new Date(2024, 0, 15, 10, 30, 0)
    const result = formatDate(date, 'YYYY-MM-DD')
    expect(result).toBe('2024-01-15')
  })

  it('应格式化时间戳', () => {
    const timestamp = new Date(2024, 0, 15).getTime()
    const result = formatDate(timestamp, 'YYYY-MM-DD')
    expect(result).toBe('2024-01-15')
  })

  it('空值应返回 "-"', () => {
    expect(formatDate(null)).toBe('-')
    expect(formatDate(undefined)).toBe('-')
    expect(formatDate('')).toBe('-')
  })
})

describe('formatMoney', () => {
  it('应格式化金额带 ¥ 前缀和千分位', () => {
    expect(formatMoney(1234.56)).toBe('¥1,234.56')
  })

  it('应支持自定义前缀', () => {
    expect(formatMoney(1234.56, '$')).toBe('$1,234.56')
  })

  it('应支持自定义小数位数', () => {
    expect(formatMoney(1234.5678, '¥', 3)).toBe('¥1,234.568')
  })

  it('应处理大数字千分位', () => {
    expect(formatMoney(1234567.89)).toBe('¥1,234,567.89')
  })

  it('应处理 0', () => {
    expect(formatMoney(0)).toBe('¥0.00')
  })

  it('应处理负数', () => {
    expect(formatMoney(-1234.56)).toBe('¥-1,234.56')
  })

  it('空值应返回 "-"', () => {
    expect(formatMoney(null)).toBe('-')
    expect(formatMoney(undefined)).toBe('-')
  })
})

describe('formatNumber', () => {
  it('应格式化数字千分位（默认 0 位小数）', () => {
    expect(formatNumber(1234)).toBe('1,234')
  })

  it('应支持指定小数位数', () => {
    expect(formatNumber(1234.5678, 2)).toBe('1,234.57')
  })

  it('应处理大数字', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
  })

  it('应处理 0', () => {
    expect(formatNumber(0)).toBe('0')
  })

  it('空值应返回 "-"', () => {
    expect(formatNumber(null)).toBe('-')
    expect(formatNumber(undefined)).toBe('-')
  })
})

describe('formatFileSize', () => {
  it('应格式化字节为 B', () => {
    expect(formatFileSize(500)).toBe('500.00 B')
  })

  it('应格式化字节为 KB', () => {
    expect(formatFileSize(1024)).toBe('1.00 KB')
  })

  it('应格式化字节为 MB', () => {
    expect(formatFileSize(1024 * 1024)).toBe('1.00 MB')
  })

  it('应格式化字节为 GB', () => {
    expect(formatFileSize(1024 * 1024 * 1024)).toBe('1.00 GB')
  })

  it('0 字节应返回 "0 B"', () => {
    expect(formatFileSize(0)).toBe('0 B')
  })

  it('应处理小数大小', () => {
    expect(formatFileSize(1536)).toBe('1.50 KB')
  })
})

describe('maskPhone', () => {
  it('应脱敏手机号（保留前 3 + 后 4 位）', () => {
    expect(maskPhone('13812341234')).toBe('138****1234')
  })

  it('应处理不同手机号', () => {
    expect(maskPhone('15900001111')).toBe('159****1111')
  })

  it('空值应返回 "-"', () => {
    expect(maskPhone(null)).toBe('-')
    expect(maskPhone(undefined)).toBe('-')
    expect(maskPhone('')).toBe('-')
  })
})

describe('maskIdCard', () => {
  it('应脱敏身份证（保留前 4 + 后 4 位）', () => {
    const result = maskIdCard('110101199001011234')
    expect(result).toMatch(/^1101\*+1234$/)
    expect(result).toContain('1234')
    expect(result.startsWith('1101')).toBe(true)
  })

  it('应处理 18 位身份证', () => {
    const result = maskIdCard('320102199001011234')
    expect(result.startsWith('3201')).toBe(true)
    expect(result.endsWith('1234')).toBe(true)
  })

  it('空值应返回 "-"', () => {
    expect(maskIdCard(null)).toBe('-')
    expect(maskIdCard(undefined)).toBe('-')
    expect(maskIdCard('')).toBe('-')
  })
})
