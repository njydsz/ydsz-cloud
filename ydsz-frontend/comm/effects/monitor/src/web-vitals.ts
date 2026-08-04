/**
 * Web Vitals 性能监控 — LCP / FID / CLS / INP / FCP / TTFB
 *
 * 使用浏览器原生 PerformanceObserver API 采集 Core Web Vitals 指标。
 * 对标 Google Web Vitals 标准 + Sentry Performance + 阿里 ARMS。
 */

/** Web Vital 指标名称 */
export type WebVitalName =
  | 'LCP'  // Largest Contentful Paint
  | 'FID'  // First Input Delay
  | 'CLS'  // Cumulative Layout Shift
  | 'INP'  // Interaction to Next Paint
  | 'FCP'  // First Contentful Paint
  | 'TTFB' // Time to First Byte
  | 'LT'   // Long Task
  | 'RT'   // Resource Timing
  ;

/** Web Vital 上报数据 */
export interface WebVitalReport {
  name: WebVitalName;
  value: number;
  rating: 'good' | 'needs-improvement' | 'poor';
  delta?: number;
  id: string;
  page: string;
  timestamp: number;
}

/** 上报端点 */
const REPORT_ENDPOINT = '/api/v1/monitor/web-vitals';

/** 已上报的指标（避免重复） */
const reportedMetrics = new Set<string>();

/** 缓冲队列 */
const vitalsQueue: WebVitalReport[] = [];

/** 最大缓冲数量（达到后立即批量上报） */
const MAX_VITALS_QUEUE_SIZE = 6;

/** 批量上报间隔（ms） */
const VITALS_FLUSH_INTERVAL = 5_000;

/** 批量上报定时器 */
let vitalsFlushTimer: null | ReturnType<typeof setTimeout> = null;

/**
 * 批量上报缓冲队列中的 Web Vitals。
 *
 * v3.4: 此前每个指标单独 sendBeacon，造成 N 次网络请求；
 * 改为批量缓冲 + 单次请求，降低网络开销。
 */
function flushVitalsQueue(): void {
  if (vitalsQueue.length === 0) return;

  const batch = vitalsQueue.splice(0, vitalsQueue.length);
  vitalsFlushTimer = null;

  try {
    const payload = JSON.stringify({ vitals: batch });
    if (navigator.sendBeacon) {
      const blob = new Blob([payload], { type: 'application/json' });
      const sent = navigator.sendBeacon(REPORT_ENDPOINT, blob);
      // sendBeacon 失败时降级 fetch
      if (!sent) {
        fetch(REPORT_ENDPOINT, {
          body: payload,
          headers: { 'Content-Type': 'application/json' },
          keepalive: true,
          method: 'POST',
        }).catch(() => {});
      }
    } else {
      fetch(REPORT_ENDPOINT, {
        body: payload,
        headers: { 'Content-Type': 'application/json' },
        keepalive: true,
        method: 'POST',
      }).catch(() => {});
    }
  } catch {
    // 静默
  }
}

/**
 * 将指标加入缓冲队列，按数量或时间触发批量上报。
 */
function enqueueVital(report: WebVitalReport): void {
  vitalsQueue.push(report);

  // 达到最大数量立即上报
  if (vitalsQueue.length >= MAX_VITALS_QUEUE_SIZE) {
    if (vitalsFlushTimer) {
      clearTimeout(vitalsFlushTimer);
      vitalsFlushTimer = null;
    }
    flushVitalsQueue();
    return;
  }

  // 延迟批量上报
  if (!vitalsFlushTimer) {
    vitalsFlushTimer = setTimeout(flushVitalsQueue, VITALS_FLUSH_INTERVAL);
  }
}

/**
 * 评分阈值（Google 标准）
 */
function getRating(name: WebVitalName, value: number): WebVitalReport['rating'] {
  const thresholds: Record<string, [number, number]> = {
    CLS: [0.1, 0.25],
    FCP: [1800, 3000],
    FID: [100, 300],
    INP: [200, 500],
    LCP: [2500, 4000],
    TTFB: [800, 1800],
  };
  const [good, poor] = thresholds[name] || [Infinity, Infinity];
  if (value <= good) return 'good';
  if (value <= poor) return 'needs-improvement';
  return 'poor';
}

/**
 * 上报单个 Web Vital 指标
 *
 * v3.4: 改为加入缓冲队列批量上报，不再逐条 sendBeacon
 */
export function reportWebVital(
  name: WebVitalName,
  value: number,
  delta?: number,
) {
  // 同一页面加载只上报一次每种指标（除 CLS 外）
  if (name !== 'CLS' && reportedMetrics.has(name)) return;
  reportedMetrics.add(name);

  const report: WebVitalReport = {
    delta,
    id: `${name}-${Date.now()}`,
    name,
    page: window.location.pathname + window.location.hash,
    rating: getRating(name, value),
    timestamp: Date.now(),
    value: Math.round(value * 100) / 100,
  };

  enqueueVital(report);

  // 开发环境打印
  if (!import.meta.env.PROD) {
    console.debug(`[Web Vitals] ${name}: ${report.value} (${report.rating})`);
  }
}

/**
 * 安装 Web Vitals 监控
 */
export function setupWebVitals() {
  // 1. LCP — 最大内容渲染
  try {
    new PerformanceObserver((list) => {
      const entries = list.getEntries();
      const lastEntry = entries[entries.length - 1];
      if (lastEntry) {
        reportWebVital('LCP', lastEntry.startTime);
      }
    }).observe({ buffered: true, type: 'largest-contentful-paint' });
  } catch {
    // 浏览器不支持
  }

  // 2. FID — 首次输入延迟
  try {
    new PerformanceObserver((list) => {
      const entries = list.getEntries();
      for (const entry of entries) {
        const fidEntry = entry as any;
        reportWebVital(
          'FID',
          fidEntry.processingStart - fidEntry.startTime,
        );
      }
    }).observe({ buffered: true, type: 'first-input' });
  } catch {
    // 浏览器不支持
  }

  // 3. CLS — 累积布局偏移
  try {
    let clsValue = 0;
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        const layoutShift = entry as any;
        if (!layoutShift.hadRecentInput) {
          clsValue += layoutShift.value;
        }
      }
      reportWebVital('CLS', clsValue);
    }).observe({ buffered: true, type: 'layout-shift' });
  } catch {
    // 浏览器不支持
  }

  // 4. INP — 交互到下次渲染
  try {
    new PerformanceObserver((list) => {
      const entries = list.getEntries();
      if (entries.length > 0) {
        const lastEntry = entries[entries.length - 1] as any;
        reportWebVital(
          'INP',
          lastEntry.processingEnd - lastEntry.startTime,
        );
      }
    }).observe({ buffered: true, type: 'event' });
  } catch {
    // 浏览器不支持
  }

  // 5. FCP — 首次内容渲染
  try {
    new PerformanceObserver((list) => {
      const entries = list.getEntries();
      if (entries.length > 0) {
        reportWebVital('FCP', entries[0].startTime);
      }
    }).observe({ buffered: true, type: 'paint' });
  } catch {
    // 浏览器不支持
  }

  // 6. TTFB — 首字节时间
  try {
    const navEntries = performance.getEntriesByType('navigation');
    if (navEntries.length > 0) {
      const navEntry = navEntries[0] as PerformanceNavigationTiming;
      const ttfb = navEntry.responseStart - navEntry.requestStart;
      if (ttfb > 0) {
        reportWebVital('TTFB', ttfb);
      }
    }
  } catch {
    // 浏览器不支持
  }

  // 7. Long Task 监控（超过 50ms 的任务）
  try {
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (entry.duration > 50) {
          reportWebVital('LT', entry.duration);
        }
      }
    }).observe({ buffered: true, type: 'longtask' });
  } catch {
    // 浏览器不支持
  }

  console.info('[Monitor] Web Vitals monitoring installed');

  // 页面卸载时强制 flush 缓冲队列，避免丢失未达批量阈值的指标
  window.addEventListener('beforeunload', flushVitalsQueue);
}
