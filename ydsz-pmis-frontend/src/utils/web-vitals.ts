/**
 * @fileoverview Web Vitals 性能指标自动上报（P3-12 落地）
 *
 * @description 使用 Google web-vitals 库自动采集核心性能指标，
 *              通过 Navigator sendBeacon API 异步上报到后端，
 *              不影响页面性能。
 *
 * 采集指标（对标 Google Core Web Vitals）：
 *   LCP (Largest Contentful Paint) — 最大内容渲染时间，理想 < 2.5s
 *   FID (First Input Delay) — 首次输入延迟，理想 < 100ms
 *   CLS (Cumulative Layout Shift) — 累积布局偏移，理想 < 0.1
 *   INP (Interaction to Next Paint) — 交互到下一次渲染，理想 < 200ms
 *   TTFB (Time to First Byte) — 首字节时间，理想 < 800ms
 *
 * 上报策略：
 *   使用 sendBeacon 异步上报，不阻塞页面卸载
 *   仅在指标值超过阈值时上报（减少无效数据）
 *   采样率可通过 VITE_WEB_VITALS_SAMPLE_RATE 控制（默认 100%）
 *
 * @module utils/web-vitals
 * @author ydsz-pmis-team
 * @since 1.3.1 (P3-12)
 */

import { onLCP, onFID, onCLS, onINP, onTTFB, type Metric } from 'web-vitals'

/** 上报端点 */
const REPORT_ENDPOINT = '/api/monitoring/web-vitals'

/** 采样率（0-1，通过环境变量配置） */
const SAMPLE_RATE = parseFloat(import.meta.env.VITE_WEB_VITALS_SAMPLE_RATE || '1.0')

/** 性能阈值（超过此值才上报） */
const THRESHOLDS: Record<string, number> = {
  LCP: 2500,   // 2.5s
  FID: 100,    // 100ms
  CLS: 0.1,    // 0.1
  INP: 200,    // 200ms
  TTFB: 800,   // 800ms
}

/** 是否已初始化 */
let initialized = false

/**
 * 上报单个性能指标。
 *
 * @param metric web-vitals 指标对象
 */
function reportMetric(metric: Metric): void {
  const threshold = THRESHOLDS[metric.name]
  if (threshold !== undefined && metric.value <= threshold) {
    // 指标在良好范围内，不上报（减少无效数据）
    return
  }

  const payload = {
    name: metric.name,
    value: parseFloat(metric.value.toFixed(3)),
    rating: metric.rating,
    delta: parseFloat(metric.delta.toFixed(3)),
    id: metric.id,
    page: window.location.pathname,
    timestamp: Date.now(),
    userAgent: navigator.userAgent,
    sessionId: getSessionId(),
  }

  // 使用 sendBeacon 异步上报（不阻塞页面卸载）
  if (navigator.sendBeacon) {
    const blob = new Blob([JSON.stringify(payload)], { type: 'application/json' })
    navigator.sendBeacon(REPORT_ENDPOINT, blob)
  } else {
    // 降级为 fetch keepalive
    fetch(REPORT_ENDPOINT, {
      method: 'POST',
      body: JSON.stringify(payload),
      headers: { 'Content-Type': 'application/json' },
      keepalive: true,
    }).catch(() => {
      // 静默失败，性能上报不应影响业务
    })
  }
}

/**
 * 获取或生成会话 ID（用于关联同一会话的多个指标）。
 */
function getSessionId(): string {
  const key = 'pmis-session-id'
  let sid = sessionStorage.getItem(key)
  if (!sid) {
    sid = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    sessionStorage.setItem(key, sid)
  }
  return sid
}

/**
 * 初始化 Web Vitals 采集。
 *
 * 在应用入口调用一次即可，后续指标会在页面生命周期中自动采集和上报。
 * 重复调用安全（幂等）。
 */
export function initWebVitals(): void {
  if (initialized) return
  initialized = true

  // 采样率控制
  if (Math.random() > SAMPLE_RATE) {
    return
  }

  // 注册所有核心指标监听
  onLCP(reportMetric)
  onFID(reportMetric)
  onCLS(reportMetric)
  onINP(reportMetric)
  onTTFB(reportMetric)

  if (import.meta.env.DEV) {
    console.log('[Web Vitals] 性能指标采集已启动，采样率:', SAMPLE_RATE)
  }
}
