/**
 * @file device 设备检测工具函数
 * @description 简单的 UA / 客户端信息解析工具。用于会话管理、设备可视化场景。
 *              规则不追求完美，只对常见浏览器/系统做归类
 * @module utils/device
 */

export interface DeviceInfo {
  /** 设备类型: DESKTOP / MOBILE / TABLET / UNKNOWN */
  device: 'DESKTOP' | 'MOBILE' | 'TABLET' | 'UNKNOWN'
  /** 操作系统 */
  os: string
  /** 浏览器 */
  browser: string
  /** 展示用短标签 */
  shortLabel: string
}

/** UA 解析结果缓存，避免相同 UA 重复正则匹配 */
const uaCache = new Map<string, DeviceInfo>()

/**
 * 解析 User Agent 字符串，提取设备类型/操作系统/浏览器等信息
 * @param ua User Agent 字符串，未传入时返回未知设备信息
 * @returns 解析后的设备信息对象（含设备类型、操作系统、浏览器、短标签）
 */
export function parseUserAgent(ua?: string): DeviceInfo {
  if (!ua) return { device: 'UNKNOWN', os: '-', browser: '-', shortLabel: '未知设备' }
  if (uaCache.has(ua)) return uaCache.get(ua)!

  const lower = ua.toLowerCase()
  let device: DeviceInfo['device'] = 'DESKTOP'
  if (/ipad|tablet|playbook|silk/.test(lower)) device = 'TABLET'
  else if (/mobile|iphone|ipod|android.*mobile|windows phone/.test(lower)) device = 'MOBILE'

  let os = '未知'
  if (/windows nt 10/.test(lower)) os = 'Windows 10/11'
  else if (/windows nt 6\.3/.test(lower)) os = 'Windows 8.1'
  else if (/windows nt 6\.2/.test(lower)) os = 'Windows 8'
  else if (/windows nt 6\.1/.test(lower)) os = 'Windows 7'
  else if (/windows/.test(lower)) os = 'Windows'
  else if (/mac os x|macintosh/.test(lower)) os = 'macOS'
  else if (/iphone os|ipad.*os/.test(lower)) os = 'iOS'
  else if (/android/.test(lower)) os = 'Android'
  else if (/linux/.test(lower)) os = 'Linux'

  let browser = '未知'
  if (/edg\//.test(lower)) browser = 'Edge'
  else if (/chrome\//.test(lower) && !/chromium/.test(lower)) browser = 'Chrome'
  else if (/firefox\//.test(lower)) browser = 'Firefox'
  else if (/safari\//.test(lower) && !/chrome/.test(lower)) browser = 'Safari'
  else if (/msie |trident\//.test(lower)) browser = 'IE'

  const deviceName =
    device === 'MOBILE' ? '手机' : device === 'TABLET' ? '平板' : device === 'DESKTOP' ? '电脑' : '未知'
  const shortLabel = `${os} · ${browser}（${deviceName}）`

  const info: DeviceInfo = { device, os, browser, shortLabel }
  uaCache.set(ua, info)
  return info
}

/**
 * 根据 DeviceInfo 返回 Element Plus 适用的图标名
 * @param device 设备类型 (DESKTOP/MOBILE/TABLET/UNKNOWN)
 * @returns 对应的 Element Plus 图标组件名
 */
export function deviceIconName(device: DeviceInfo['device']): string {
  if (device === 'MOBILE') return 'Iphone'
  if (device === 'TABLET') return 'Tablet'
  if (device === 'DESKTOP') return 'Monitor'
  return 'QuestionFilled'
}
