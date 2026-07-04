/**
 * @file 页面性能监控 composable
 * @description 自动记录组件挂载时间和 Web Vitals 指标
 * @module composables/usePerformance
 */
import { onMounted } from 'vue'
import { logger } from '@/utils/logger'

/**
 * 页面性能监控 composable
 * 自动记录组件挂载时间和 Web Vitals 指标
 */
export function usePerformance(componentName: string) {
  const mountTime = performance.now()

  onMounted(() => {
    const duration = performance.now() - mountTime
    logger.debug('[Performance]', `${componentName} mounted in ${duration.toFixed(2)}ms`)

    // 上报到 Sentry
    if (import.meta.env.PROD) {
      import('@/utils/sentry').then(({ captureMeasurement }) => {
        captureMeasurement?.('component.mount', duration, { componentName })
      })
    }
  })
}

/**
 * 记录 Web Vitals 指标
 */
export function reportWebVitals() {
  if (typeof window === 'undefined') return

  // 使用 PerformanceObserver 监听 LCP、CLS
  try {
    // LCP (Largest Contentful Paint)
    const lcpObserver = new PerformanceObserver((list) => {
      const entries = list.getEntries()
      const lastEntry = entries[entries.length - 1]
      logger.debug('[WebVitals] LCP:', `${lastEntry.startTime.toFixed(2)}ms`)
    })
    lcpObserver.observe({ type: 'largest-contentful-paint', buffered: true } as PerformanceObserverInit)

    // CLS (Cumulative Layout Shift)
    let clsValue = 0
    const clsObserver = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const entryValue = (entry as unknown as { value: number; hadRecentInput: boolean }).value
        const hadRecentInput = (entry as unknown as { hadRecentInput: boolean }).hadRecentInput
        if (!hadRecentInput) {
          clsValue += entryValue
        }
      }
      logger.debug('[WebVitals] CLS:', clsValue.toFixed(4))
    })
    clsObserver.observe({ type: 'layout-shift', buffered: true } as PerformanceObserverInit)
  } catch {
    // PerformanceObserver 不支持时静默降级
  }
}
