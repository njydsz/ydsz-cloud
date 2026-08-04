/**
 * 错误监控 — Vue + window + Promise + 资源加载错误捕获
 *
 * v3.1 增强：
 *   - 会话追踪：每个错误携带 sessionId / traceId / release，支持全链路关联
 *   - 采样控制：sampleRate + beforeSend 钩子，防止高频错误打满上报队列
 *   - sourcemap 关联：release 字段供后端匹配 sourcemap 符号化 stack trace
 *
 * v3.4 增强：
 *   - 面包屑：错误上报时附带最近 N 条用户行为轨迹
 *   - 离线恢复：监听 online 事件自动重放离线缓存的上报
 *
 * 对标 Sentry / 阿里 ARMS / 腾讯 APM 的前端错误采集能力。
 */

import { getBreadcrumbs, type Breadcrumb } from './breadcrumb';

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
  /** v3.1: 会话 ID，单次页面生命周期唯一 */
  sessionId?: string;
  /** v3.1: 错误追踪 ID，单条错误唯一，便于后端关联 */
  traceId?: string;
  /** v3.1: 发布版本（commit hash），用于 sourcemap 符号化 */
  release?: string;
  /** v3.4: 错误发生前的用户行为面包屑 */
  breadcrumbs?: Breadcrumb[];
  extra?: Record<string, any>;
}

/** 监控配置选项 */
export interface MonitorConfig {
  /** 发布版本标识（commit hash / 版本号），用于 sourcemap 关联 */
  release?: string;
  /** 采样率 0~1，默认 1（全量上报） */
  sampleRate?: number;
  /** 上报前钩子，返回 false 丢弃该错误，返回修改后的 report 可脱敏 */
  beforeSend?: (report: ErrorReport) => ErrorReport | null;
  /** 动态获取用户 ID（如从 Pinia store） */
  getUserId?: () => string | undefined;
  /** 上报失败时是否自动重试，默认 true */
  retry?: boolean;
  /** 最大重试次数，默认 3 */
  maxRetries?: number;
  /** 重试基础延迟（ms），默认 1000 */
  retryBaseDelay?: number;
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

/** 单类型最大排队数（防止单一错误类型打满队列） */
const MAX_PER_TYPE = 5;

/** 离线缓存键名 */
const OFFLINE_CACHE_KEY = 'ydsz_monitor_offline_queue';

/** 离线缓存最大条数 */
const MAX_OFFLINE_CACHE = 100;

/** 当前监控配置 */
let monitorConfig: MonitorConfig = {};

/** 当前会话 ID（页面生命周期内唯一） */
let sessionId = '';

/** 生成唯一 traceId */
function generateTraceId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/** 初始化会话 ID */
function ensureSessionId(): string {
  if (!sessionId) {
    sessionId = generateTraceId();
  }
  return sessionId;
}

/**
 * 为错误报告注入会话追踪字段
 *
 * v3.4: 同时附带当前面包屑快照，便于后端复现错误路径
 */
function enrichReport(report: ErrorReport): ErrorReport {
  return {
    ...report,
    sessionId: ensureSessionId(),
    traceId: generateTraceId(),
    release: monitorConfig.release,
    userId: report.userId || monitorConfig.getUserId?.(),
    breadcrumbs: getBreadcrumbs(),
  };
}

/**
 * 添加错误到队列并触发批量上报
 *
 * v3.1: 应用采样率 + beforeSend 钩子 + 单类型限流
 */
function enqueueError(rawReport: ErrorReport) {
  // 采样：在 enrich 之前按 sampleRate 采样，避免无效处理
  const sampleRate = monitorConfig.sampleRate ?? 1;
  if (sampleRate < 1 && Math.random() > sampleRate) {
    return;
  }

  let report = enrichReport(rawReport);

  // beforeSend 钩子：可丢弃或脱敏
  if (monitorConfig.beforeSend) {
    const result = monitorConfig.beforeSend(report);
    if (!result) return;
    report = result;
  }

  // 避免重复上报同一错误（10秒内）
  const isDuplicate = errorQueue.some(
    (item) =>
      item.type === report.type &&
      item.message === report.message &&
      Date.now() - item.timestamp < 10_000,
  );
  if (isDuplicate) return;

  // 单类型限流：同类型错误超过阈值时丢弃（防止单一错误源打满队列）
  const sameTypeCount = errorQueue.filter((e) => e.type === report.type).length;
  if (sameTypeCount >= MAX_PER_TYPE) {
    return;
  }

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
 *
 * v3.2: 支持重试 + 离线缓存
 */
function flush() {
  if (errorQueue.length === 0) return;

  const batch = errorQueue.splice(0, errorQueue.length);
  flushTimer = null;

  sendBatch(batch, 0);
}

/**
 * 发送错误批次，支持重试
 */
function sendBatch(batch: ErrorReport[], retryCount: number): void {
  const maxRetries = monitorConfig.maxRetries ?? 3;
  const retryBaseDelay = monitorConfig.retryBaseDelay ?? 1000;
  const shouldRetry = monitorConfig.retry !== false;

  try {
    // 使用 sendBeacon 确保页面卸载时也能上报
    if (navigator.sendBeacon) {
      const blob = new Blob([JSON.stringify({ errors: batch })], {
        type: 'application/json',
      });
      const sent = navigator.sendBeacon(REPORT_ENDPOINT, blob);
      if (!sent && shouldRetry && retryCount < maxRetries) {
        scheduleRetry(batch, retryCount, retryBaseDelay);
      }
    } else {
      // 降级 fetch
      fetch(REPORT_ENDPOINT, {
        body: JSON.stringify({ errors: batch }),
        headers: { 'Content-Type': 'application/json' },
        keepalive: true,
        method: 'POST',
      })
        .then((res) => {
          if (!res.ok && shouldRetry && retryCount < maxRetries) {
            scheduleRetry(batch, retryCount, retryBaseDelay);
          }
        })
        .catch(() => {
          if (shouldRetry && retryCount < maxRetries) {
            scheduleRetry(batch, retryCount, retryBaseDelay);
          } else {
            // 重试耗尽，缓存到本地存储
            cacheForOffline(batch);
          }
        });
    }
  } catch {
    // 上报失败，尝试缓存
    cacheForOffline(batch);
  }
}

/**
 * 调度重试（指数退避 + 抖动）
 */
function scheduleRetry(batch: ErrorReport[], retryCount: number, baseDelay: number): void {
  // 指数退避：baseDelay * 2^retryCount
  const delay = baseDelay * Math.pow(2, retryCount);
  // 添加抖动：±25%
  const jitter = delay * 0.25 * (Math.random() * 2 - 1);
  const finalDelay = Math.max(0, delay + jitter);

  setTimeout(() => {
    sendBatch(batch, retryCount + 1);
  }, finalDelay);
}

/**
 * 缓存错误到本地存储（离线场景）
 */
function cacheForOffline(batch: ErrorReport[]): void {
  try {
    const cached = loadOfflineCache();
    const merged = [...cached, ...batch].slice(-MAX_OFFLINE_CACHE);
    localStorage.setItem(OFFLINE_CACHE_KEY, JSON.stringify(merged));
  } catch {
    // 存储失败静默
  }
}

/**
 * 加载离线缓存的错误
 */
function loadOfflineCache(): ErrorReport[] {
  try {
    const data = localStorage.getItem(OFFLINE_CACHE_KEY);
    return data ? JSON.parse(data) : [];
  } catch {
    return [];
  }
}

/**
 * 清空离线缓存
 */
function clearOfflineCache(): void {
  try {
    localStorage.removeItem(OFFLINE_CACHE_KEY);
  } catch {
    // 静默
  }
}

/**
 * 恢复离线缓存的错误（网络恢复时调用）
 */
function restoreOfflineCache(): void {
  const cached = loadOfflineCache();
  if (cached.length > 0) {
    clearOfflineCache();
    sendBatch(cached, 0);
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
 * 在 app.mount() 之前调用 setupErrorMonitoring(app, config)
 * v3.1: config 支持采样率、beforeSend、release 版本标识
 */
export function setupErrorMonitoring(app: any, config: MonitorConfig = {}) {
  monitorConfig = config;
  ensureSessionId();

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

  // 5. 网络恢复时自动重放离线缓存的上报
  //    v3.4: 此前 restoreOfflineCache 已定义但从未调用，导致离线缓存写而不读
  window.addEventListener('online', restoreOfflineCache);

  // 启动时若已在线，尝试重放上次会话遗留的离线缓存
  if (navigator.onLine) {
    restoreOfflineCache();
  }

  console.info('[Monitor] Error monitoring installed', {
    release: config.release,
    sampleRate: config.sampleRate ?? 1,
  });
}
