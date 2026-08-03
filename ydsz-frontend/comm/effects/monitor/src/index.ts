/**
 * @ydsz/monitor — 前端监控公共模块
 *
 * 包含两大能力：
 * 1. 错误监控：Vue errorHandler + window.onerror + unhandledrejection + 资源加载错误
 * 2. Web Vitals 性能监控：LCP / FID / CLS / INP / FCP / TTFB
 *
 * 上报方式：通过 navigator.sendBeacon 发送到后端 /api/v1/monitor/report
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

export {
  setupMonitor,
} from './setup';
