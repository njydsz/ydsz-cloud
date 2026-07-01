/**
 * @file 通用格式化工具
 * @description 提供日期、金额、数字、文件大小、敏感数据脱敏等格式化方法
 * @module utils/format
 *
 * 设计原则：
 *  - 所有方法对 null/undefined 输入返回 '-'，避免模板渲染 'null' 字符串
 *  - 金额默认 ¥ 前缀 + 2 位小数 + 千分位，符合财务展示规范
 *  - 脱敏方法仅做展示层处理，原始数据仍保留在内存中
 */
import dayjs from 'dayjs'

/**
 * 格式化日期
 * @param value - 日期值，支持 string/number/Date
 * @param format - dayjs 格式化模板，默认 'YYYY-MM-DD HH:mm:ss'
 * @returns 格式化后的日期字符串，空值返回 '-'
 */
export function formatDate(value: string | number | Date | null | undefined, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!value) return '-'
  return dayjs(value).format(format)
}

/**
 * 格式化金额（含货币前缀 + 千分位 + 小数）
 * @param value - 金额数值
 * @param prefix - 货币前缀，默认 '¥'
 * @param decimals - 小数位数，默认 2
 * @returns 形如 '¥1,234.56' 的字符串
 */
export function formatMoney(value: number | null | undefined, prefix = '¥', decimals = 2): string {
  if (value === null || value === undefined) return '-'
  return `${prefix}${value.toFixed(decimals).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`
}

/**
 * 格式化数字（千分位）
 * @param value - 数值
 * @param decimals - 小数位数，默认 0
 * @returns 形如 '1,234' 的字符串
 */
export function formatNumber(value: number | null | undefined, decimals = 0): string {
  if (value === null || value === undefined) return '-'
  return value.toFixed(decimals).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/**
 * 文件大小格式化（自动选择 B/KB/MB/GB/TB 单位）
 * @param bytes - 字节数
 * @returns 形如 '1.50 MB' 的字符串
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

/**
 * 脱敏手机号（保留前 3 + 后 4 位）
 * @param phone - 11 位手机号
 * @returns 形如 '138****1234' 的字符串
 */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '-'
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/**
 * 脱敏身份证（保留前 4 + 后 4 位）
 * @param idCard - 18 位身份证号
 * @returns 形如 '1101***********1234' 的字符串
 */
export function maskIdCard(idCard: string | null | undefined): string {
  if (!idCard) return '-'
  return idCard.replace(/(\d{4})\d+(\d{4})/, '$1***********$2')
}
