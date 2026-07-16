/**
 * @file 页面性能监控 composable
 * @description
 *   1. usePerformance(componentName)：组件挂载耗时监控
 *   2. reportWebVitals()：全局 Web Vitals 指标采集与上报（LCP/CLS/INP/TTFB/FCP）
 *
 * Web Vitals 上报链路：
 *   - 开发环境：logger.debug 控制台输出
 *   - 生产环境：
 *     a) Sentry captureMeasurement（性能面包屑）
 *     b) navigator.sendBeacon → /api/vitals（后端聚合分析，需开启 VITE_ENABLE_VITALS_BEACON）
 *
 * 评级阈值遵循 Google Web Vitals 标准：
 *   - LCP:  ≤2500ms good / ≤4000ms needs-improvement / >4000ms poor
 *   - CLS:  ≤0.1    good / ≤0.25   needs-improvement / >0.25   poor
 *   - INP:  ≤200ms  good / ≤500ms  needs-improvement / >500ms  poor
 *   - TTFB: ≤800ms  good / ≤1800ms needs-improvement / >1800ms poor
 *   - FCP:  ≤1800ms good / ≤3000ms needs-improvement / >3000ms poor
 *
 * @module composables/usePerformance
 */
import { onMounted } from 'vue'
import { logger } from '@/utils/logger'

// ==================== 类型定义 ====================

/** Web Vitals 指标名称 */
export type WebVitalName = 'LCP' | 'CLS' | 'INP' | 'TTFB' | 'FCP'

/** 指标评级 */
export type WebVitalRating = 'good' | 'needs-improvement' | 'poor'

/** 单条 Web Vitals 指标 */
export interface WebVitalMetric {
  name: WebVitalName
  value: number
  rating: WebVitalRating
  delta: number
  id: string
  navigationType: string
  entries: PerformanceEntry[]
}

/** layout-shift 性能条目扩展（TS DOM lib 未完整覆盖） */
interface LayoutShiftEntry extends PerformanceEntry {
  value: number
  hadRecentInput: boolean
}

/** event timing 性能条目扩展（INP 依赖） */
interface EventTimingEntry extends PerformanceEntry {
  interactionId: number
  processingStart: number
  processingEnd: number
  duration: number
}

/** 后端 beacon 上报载荷 */
interface VitalBeaconPayload {
  name: WebVitalName
  value: number
  rating: WebVitalRating
  delta: number
  id: string
  navigationType: string
  page: string
  timestamp: number
}

// ==================== 评级阈值（Google Web Vitals 标准） ====================

const RATING_THRESHOLDS: Record<WebVitalName, { good: number; needsImprovement: number }> = {
  LCP: { good: 2500, needsImprovement: 4000 },
  CLS: { good: 0.1, needsImprovement: 0.25 },
  INP: { good: 200, needsImprovement: 500 },
  TTFB: { good: 800, needsImprovement: 1800 },
  FCP: { good: 1800, needsImprovement: 3000 },
}

/** 生成指标唯一 ID */
function generateId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

/** 根据指标名和值计算评级 */
function computeRating(name: WebVitalName, value: number): WebVitalRating {
  const thresholds = RATING_THRESHOLDS[name]
  if (value <= thresholds.good) return 'good'
  if (value <= thresholds.needsImprovement) return 'needs-improvement'
  return 'poor'
}

/** 获取 navigation type（navigate/reload/back_forward） */
function getNavigationType(): string {
  try {
    const entries = performance.getEntriesByType('navigation')
    const nav = entries[0] as PerformanceNavigationTiming | undefined
    return nav?.type || 'unknown'
  } catch {
    return 'unknown'
  }
}

// ==================== 批量上报通道（P1-5 增强） ====================

/** 批量上报缓冲区 */
const batchBuffer: VitalBeaconPayload[] = []
/** 批量上报阈值 */
const BATCH_SIZE = 5
/** 批量上报超时（ms） */
const BATCH_TIMEOUT_MS = 5000
/** 批量定时器 */
let batchTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 刷新批量缓冲区，通过 sendBeacon 一次性上报
 */
function flushBatch(): void {
  if (batchBuffer.length === 0) return

  const payload = JSON.stringify({
    metrics: batchBuffer.splice(0),
    page: location.pathname,
    timestamp: Date.now(),
  })

  try {
    const blob = new Blob([payload], { type: 'application/json' })
    navigator.sendBeacon('/api/vitals/batch', blob)
  } catch {
    // 静默失败
  }

  if (batchTimer) {
    clearTimeout(batchTimer)
    batchTimer = null
  }
}

/**
 * 将指标加入批量缓冲区
 */
function addToBatch(payload: VitalBeaconPayload): void {
  batchBuffer.push(payload)

  if (batchBuffer.length >= BATCH_SIZE) {
    flushBatch()
  } else if (!batchTimer) {
    batchTimer = setTimeout(flushBatch, BATCH_TIMEOUT_MS)
  }
}

// 页面隐藏时刷新剩余指标
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      flushBatch()
    }
  })
  window.addEventListener('pagehide', flushBatch)
}

// ==================== 上报通道 ====================

/** 已上报的指标集合，避免重复上报 */
const reportedMetrics = new Set<WebVitalName>()

/**
 * 上报指标到后端（beacon）
 * 生产环境且开启 VITE_ENABLE_VITALS_BEACON 时启用批量上报，避免开发环境产生 404 噪音
 */
function reportToBackend(metric: WebVitalMetric): void {
  if (!import.meta.env.PROD) return
  if (!import.meta.env.VITE_ENABLE_VITALS_BEACON) return
  if (typeof navigator === 'undefined' || typeof navigator.sendBeacon !== 'function') return

  try {
    const payload: VitalBeaconPayload = {
      name: metric.name,
      value: Number(metric.value.toFixed(4)),
      rating: metric.rating,
      delta: Number(metric.delta.toFixed(4)),
      id: metric.id,
      navigationType: metric.navigationType,
      page: location.pathname,
      timestamp: Date.now(),
    }
    // P1-5 增强：使用批量上报代替逐条上报
    addToBatch(payload)
  } catch {
    // 静默失败，性能上报不能影响主流程
  }
}

/**
 * 上报指标到 Sentry（captureMeasurement 性能面包屑）
 * 动态加载 sentry 工具，避免主 bundle 体积膨胀
 */
async function reportToSentry(metric: WebVitalMetric): Promise<void> {
  if (!import.meta.env.PROD) return
  try {
    const { captureMeasurement } = await import('@/utils/sentry')
    captureMeasurement?.(`web_vital.${metric.name}`, metric.value, {
      rating: metric.rating,
      delta: metric.delta,
      navigationType: metric.navigationType,
    })
  } catch {
    // Sentry 不可用时静默降级
  }
}

/**
 * 指标上报统一入口
 * 同时触发 console（开发）、Sentry、后端 beacon 三个通道
 */
function emitMetric(metric: WebVitalMetric): void {
  if (reportedMetrics.has(metric.name)) return
  reportedMetrics.add(metric.name)

  logger.debug(
    '[WebVitals]',
    `${metric.name}=${metric.value.toFixed(metric.name === 'CLS' ? 4 : 2)} (${metric.rating})`,
  )

  reportToBackend(metric)
  void reportToSentry(metric)
}

// ==================== 各指标 PerformanceObserver ====================

/**
 * 监听 LCP (Largest Contentful Paint)
 * LCP 在页面整个生命周期可能多次触发，取最后一次（visibilitychange hidden 时锁定）
 */
function observeLCP(): void {
  if (typeof PerformanceObserver === 'undefined') return
  let lastEntry: PerformanceEntry | null = null

  try {
    const observer = new PerformanceObserver((list) => {
      const entries = list.getEntries()
      if (entries.length > 0) {
        lastEntry = entries[entries.length - 1]
      }
    })
    observer.observe({ type: 'largest-contentful-paint', buffered: true } as PerformanceObserverInit)

    // 页面隐藏时锁定 LCP 最终值并上报
    const report = () => {
      if (!lastEntry) return
      const value = lastEntry.startTime
      emitMetric({
        name: 'LCP',
        value,
        rating: computeRating('LCP', value),
        delta: value,
        id: generateId(),
        navigationType: getNavigationType(),
        entries: [lastEntry],
      })
      observer.disconnect()
      document.removeEventListener('visibilitychange', report)
    }
    document.addEventListener('visibilitychange', report, { once: true })
  } catch {
    // 浏览器不支持 largest-contentful-paint 时静默降级
  }
}

/**
 * 监听 CLS (Cumulative Layout Shift)
 * 持续累加 layout-shift（排除用户输入 500ms 内的位移），页面隐藏时上报累计值
 */
function observeCLS(): void {
  if (typeof PerformanceObserver === 'undefined') return
  let clsValue = 0
  let lastEntry: PerformanceEntry | null = null

  try {
    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const ls = entry as LayoutShiftEntry
        if (!ls.hadRecentInput) {
          clsValue += ls.value
          lastEntry = entry
        }
      }
    })
    observer.observe({ type: 'layout-shift', buffered: true } as PerformanceObserverInit)

    const report = () => {
      if (clsValue <= 0) return
      emitMetric({
        name: 'CLS',
        value: clsValue,
        rating: computeRating('CLS', clsValue),
        delta: clsValue,
        id: generateId(),
        navigationType: getNavigationType(),
        entries: lastEntry ? [lastEntry] : [],
      })
      observer.disconnect()
      document.removeEventListener('visibilitychange', report)
    }
    document.addEventListener('visibilitychange', report, { once: true })
  } catch {
    // 浏览器不支持 layout-shift 时静默降级
  }
}

/**
 * 监听 INP (Interaction to Next Paint)
 * 收集所有带 interactionId 的事件，取最差（最大）duration 作为 INP 值
 * INP 是 2024 年正式取代 FID 的响应性指标
 */
function observeINP(): void {
  if (typeof PerformanceObserver === 'undefined') return
  let worstDuration = 0
  let worstEntry: PerformanceEntry | null = null

  try {
    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const et = entry as EventTimingEntry
        // 仅统计交互事件（interactionId > 0）
        if (et.interactionId > 0 && et.duration > worstDuration) {
          worstDuration = et.duration
          worstEntry = entry
        }
      }
    })
    // event 类型在部分浏览器需要 extraThresholds 配置
    observer.observe({ type: 'event', buffered: true } as PerformanceObserverInit)

    const report = () => {
      if (worstDuration <= 0) return
      emitMetric({
        name: 'INP',
        value: worstDuration,
        rating: computeRating('INP', worstDuration),
        delta: worstDuration,
        id: generateId(),
        navigationType: getNavigationType(),
        entries: worstEntry ? [worstEntry] : [],
      })
      observer.disconnect()
      document.removeEventListener('visibilitychange', report)
    }
    document.addEventListener('visibilitychange', report, { once: true })
  } catch {
    // 浏览器不支持 event timing 时静默降级
  }
}

/**
 * 监听 FCP (First Contentful Paint)
 * 首次内容绘制，一次性指标，触发即上报
 */
function observeFCP(): void {
  if (typeof PerformanceObserver === 'undefined') return
  try {
    const observer = new PerformanceObserver((list) => {
      const entries = list.getEntries()
      const fcpEntry = entries.find((e) => e.name === 'first-contentful-paint')
      if (!fcpEntry) return
      const value = fcpEntry.startTime
      emitMetric({
        name: 'FCP',
        value,
        rating: computeRating('FCP', value),
        delta: value,
        id: generateId(),
        navigationType: getNavigationType(),
        entries: [fcpEntry],
      })
      observer.disconnect()
    })
    observer.observe({ type: 'paint', buffered: true } as PerformanceObserverInit)
  } catch {
    // 静默降级
  }
}

/**
 * 监听 TTFB (Time to First Byte)
 * 从导航开始到首字节到达的时间，基于 Navigation Timing API
 */
function observeTTFB(): void {
  try {
    const entries = performance.getEntriesByType('navigation')
    const nav = entries[0] as PerformanceNavigationTiming | undefined
    if (!nav) return
    // TTFB = responseStart - requestStart（首字节到达 - 请求发出）
    const value = nav.responseStart - nav.requestStart
    if (!Number.isFinite(value) || value < 0) return
    emitMetric({
      name: 'TTFB',
      value,
      rating: computeRating('TTFB', value),
      delta: value,
      id: generateId(),
      navigationType: nav.type || 'unknown',
      entries: [nav],
    })
  } catch {
    // 静默降级
  }
}

// ==================== 对外 API ====================

/**
 * 组件挂载耗时监控 composable
 *
 * 在组件 setup 中调用，自动记录 setup 开始到 onMounted 的耗时
 *
 * @example
 * ```ts
 * usePerformance('FlowDesigner')
 * ```
 */
export function usePerformance(componentName: string): void {
  const mountTime = performance.now()

  onMounted(() => {
    const duration = performance.now() - mountTime
    logger.debug('[Performance]', `${componentName} mounted in ${duration.toFixed(2)}ms`)

    if (import.meta.env.PROD) {
      void import('@/utils/sentry').then(({ captureMeasurement }) => {
        captureMeasurement?.('component.mount', duration, { componentName })
      })
    }
  })
}

/**
 * 启动全局 Web Vitals 监控
 *
 * 在 main.ts 中调用一次即可，自动采集 LCP/CLS/INP/TTFB/FCP 并上报
 * - LCP/FCP/TTFB：值确定后立即上报
 * - CLS/INP：在页面 visibilitychange（hidden）时上报最终累计值
 */
export function reportWebVitals(): void {
  if (typeof window === 'undefined') return

  // TTFB 同步采集（Navigation Timing 已就绪）
  observeTTFB()
  // FCP 一次性指标
  observeFCP()
  // LCP 取最终值
  observeLCP()
  // CLS 累计值
  observeCLS()
  // INP 最差交互
  observeINP()
}
