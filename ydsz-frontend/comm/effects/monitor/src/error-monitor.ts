/**
 * 错误监控 — Vue + window + Promise + 资源加载错误捕获
 *
 * 对标 Sentry / 阿里 ARMS / 腾讯 APM 的前端错误采集能力。
 */

/** 错误事件类型 */
export type ErrorType =
  | 'vue'
  | 'window'
  | 'promise'
  | 'resource';

/** 错误上报数据结构 */
export interface ErrorReport {
  type: ErrorType;
  message: string;
  stack?: string;
  filename?: string;
  lineno?: number;
  colno?: number;
  url?: string;
  timestamp: number;
  userAgent: string;
  appVersion?: string;
  userId?: string;
  route?: string;
  extra?: Record<string, any>;
}

/** 上报端点 */
const REPORT_ENDPOINT = '/api/v1/monitor/error';

/** 错误缓冲队列（批量上报） */
const errorQueue: ErrorReport[] = [];

/** 上报定时器 */
let flushTimer: null | ReturnType<typeof setTimeout> = null;

/** 最大缓冲数量 */
const MAX_QUEUE_SIZE = 10;

/** 上报间隔（ms） */
const FLUSH_INTERVAL = 10_000;

/**
 * 添加错误到队列并触发批量上报
 */
function enqueueError(report: ErrorReport) {
  // 避免重复上报同一错误（10秒内）
  const isDuplicate = errorQueue.some(
    (item) =>
      item.type === report.type &&
      item.message === report.message &&
      Date.now() - item.timestamp < 10_000,
  );
  if (isDuplicate) return;

  errorQueue.push(report);

  // 达到最大数量立即上报
  if (errorQueue.length >= MAX_QUEUE_SIZE) {
    flush();
    return;
  }

  // 延迟批量上报
  if (flushTimer) clearTimeout(flushTimer);
  flushTimer = setTimeout(flush, FLUSH_INTERVAL);
}

/**
 * 批量上报错误到后端
 */
function flush() {
  if (errorQueue.length === 0) return;

  const batch = errorQueue.splice(0, errorQueue.length);
  flushTimer = null;

  try {
    // 使用 sendBeacon 确保页面卸载时也能上报
    if (navigator.sendBeacon) {
      const blob = new Blob([JSON.stringify({ errors: batch })], {
        type: 'application/json',
      });
      navigator.sendBeacon(REPORT_ENDPOINT, blob);
    } else {
      // 降级 fetch
      fetch(REPORT_ENDPOINT, {
        body: JSON.stringify({ errors: batch }),
        headers: { 'Content-Type': 'application/json' },
        keepalive: true,
        method: 'POST',
      }).catch(() => {});
    }
  } catch {
    // 上报失败静默
  }
}

/**
 * 手动上报错误
 */
export function reportError(
  type: ErrorType,
  message: string,
  extra?: Record<string, any>,
) {
  enqueueError({
    colno: undefined,
    filename: undefined,
    lineno: undefined,
    message,
    stack: undefined,
    timestamp: Date.now(),
    type,
    url: window.location.href,
    userAgent: navigator.userAgent,
    extra,
  });
}

/**
 * 获取当前路由路径
 */
function getCurrentRoute(): string {
  return window.location.pathname + window.location.hash;
}

/**
 * 安装错误监控
 *
 * 在 app.mount() 之前调用 setupErrorMonitoring(app)
 */
export function setupErrorMonitoring(app: any) {
  // 1. Vue 组件错误
  app.config.errorHandler = (err: any, _instance: any, info: string) => {
    const report: ErrorReport = {
      message: err?.message || String(err),
      stack: err?.stack,
      timestamp: Date.now(),
      type: 'vue',
      url: getCurrentRoute(),
      userAgent: navigator.userAgent,
      extra: { lifecycleHook: info },
    };
    enqueueError(report);

    // 开发环境打印
    if (!import.meta.env.PROD) {
      console.error('[Vue Error]', err, info);
    }
  };

  // 2. window 全局错误
  window.addEventListener('error', (event) => {
    // 资源加载错误
    if (event.target && (event.target as any).src) {
      const target = event.target as any;
      const report: ErrorReport = {
        message: `Resource load failed: ${target.src || target.href}`,
        filename: target.src || target.href,
        timestamp: Date.now(),
        type: 'resource',
        url: getCurrentRoute(),
        userAgent: navigator.userAgent,
        extra: { tagName: target.tagName },
      };
      enqueueError(report);
      return;
    }

    // JS 运行时错误
    const report: ErrorReport = {
      colno: event.colno,
      filename: event.filename,
      lineno: event.lineno,
      message: event.message,
      stack: event.error?.stack,
      timestamp: Date.now(),
      type: 'window',
      url: getCurrentRoute(),
      userAgent: navigator.userAgent,
    };
    enqueueError(report);
  }, true); // 使用捕获阶段以获取资源错误

  // 3. Promise 未捕获异常
  window.addEventListener('unhandledrejection', (event) => {
    const reason = event.reason;
    const report: ErrorReport = {
      message: reason?.message || String(reason),
      stack: reason?.stack,
      timestamp: Date.now(),
      type: 'promise',
      url: getCurrentRoute(),
      userAgent: navigator.userAgent,
      extra: reason?.config
        ? { url: reason.config.url, method: reason.config.method }
        : undefined,
    };
    enqueueError(report);
  });

  // 4. 页面卸载时强制上报
  window.addEventListener('beforeunload', flush);

  console.info('[Monitor] Error monitoring installed');
}
