import dayjs from 'dayjs'

/**
 * 格式化日期
 */
export function formatDate(value: string | number | Date | null | undefined, format = 'YYYY-MM-DD HH:mm:ss'): string {
  if (!value) return '-'
  return dayjs(value).format(format)
}

/**
 * 格式化金额
 */
export function formatMoney(value: number | null | undefined, prefix = '¥', decimals = 2): string {
  if (value === null || value === undefined) return '-'
  return `${prefix}${value.toFixed(decimals).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`
}

/**
 * 格式化数字（千分位）
 */
export function formatNumber(value: number | null | undefined, decimals = 0): string {
  if (value === null || value === undefined) return '-'
  return value.toFixed(decimals).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/**
 * 文件大小格式化
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

/**
 * 脱敏手机号
 */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return '-'
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/**
 * 脱敏身份证
 */
export function maskIdCard(idCard: string | null | undefined): string {
  if (!idCard) return '-'
  return idCard.replace(/(\d{4})\d+(\d{4})/, '$1***********$2')
}
