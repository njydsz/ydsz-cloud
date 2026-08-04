/**
 * @ydsz/monitor — 前端监控公共模块
 *
 * 包含三大能力：
 * 1. 错误监控：Vue errorHandler + window.onerror + unhandledrejection + 资源加载错误
 * 2. Web Vitals 性能监控：LCP / FID / CLS / INP / FCP / TTFB
 * 3. 面包屑：用户行为轨迹，错误上报时附带
 *
 * 上报方式：通过 navigator.sendBeacon 批量发送到后端 /api/v1/monitor/*
 */

export {
  setupErrorMonitoring,
  reportError,
} from './error-monitor';
export type { ErrorType, ErrorReport, MonitorConfig } from './error-monitor';

export {
  setupWebVitals,
  reportWebVital,
} from './web-vitals';
export type { WebVitalName, WebVitalReport } from './web-vitals';

export {
  addBreadcrumb,
  clearBreadcrumbs,
  getBreadcrumbs,
  setupBreadcrumbAutoCapture,
} from './breadcrumb';
export type { Breadcrumb, BreadcrumbCategory, BreadcrumbLevel } from './breadcrumb';

export {
  setupMonitor,
} from './setup';
