/**
 * Sentry 上报适配层（P0-3）
 *
 * 设计目标：
 * - 作为可选通道：自研监控体系为主，Sentry 为可选外部 APM 转发
 * - 软依赖：@sentry/vue 缺失时不报错，仅输出警告
 * - 与 error-monitor 解耦：通过 captureError(report) 接口转发标准化错误格式
 * - Sourcemap 符号化：初始化 Sentry 时绑定 release 版本，Sentry CLI 在 CI 中上传 sourcemap
 *
 * Sourcemap 集成流程：
 * 1. vite-config 启用 sourcemap: 'hidden'（生产构建产物附带 sourcemap 但浏览器不加载）
 * 2. CI 中调用 sentry-cli releases files <release> upload-sourcemaps ./dist
 * 3. Sentry 根据 release + 符号化 stack trace，展示为源码位置
 *
 * 使用方式：
 * ```ts
 * import { initSentry } from '@ydsz/monitor/sentry';
 *
 * // 在 bootstrap 中调用
 * if (import.meta.env.VITE_SENTRY_DSN) {
 *   await initSentry({
 *     dsn: import.meta.env.VITE_SENTRY_DSN,
 *     release: import.meta.env.VITE_APP_RELEASE,
 *     environment: import.meta.env.MODE,
 *     sampleRate: 0.8,
 *   });
 * }
 * ```
 *
 * @path comm/effects/monitor/src/sentry.ts
 * @author ydsz-team
 * @since 4.0.0
 */

import type { ErrorReport } from './error-monitor';

/** Sentry 初始化选项 */
export interface SentryConfig {
  /** Sentry DSN（来自 sentry.io 项目设置） */
  dsn: string;
  /** 发布版本（commit hash），用于 sourcemap 符号化与 release 对比 */
  release?: string;
  /** 环境：development / production / staging */
  environment?: string;
  /** 错误采样率 0~1，默认 1 */
  sampleRate?: number;
  /** 性能监控采样率 0~1，默认 0（不采样 traces） */
  tracesSampleRate?: number;
  /** Vue 应用实例（用于 Vue 错误上下文） */
  app?: any;
}

/** Sentry SDK 模块缓存（动态导入） */
let sentryModule: any = null;
/** 是否已完成初始化 */
let initialized = false;
/** 当前配置 */
let currentConfig: SentryConfig | null = null;

/**
 * 初始化 Sentry SDK
 *
 * 通过动态导入 @sentry/vue 避免硬依赖 —— 未安装该包时静默降级。
 * 重复调用幂等（仅初始化一次）。
 */
export async function initSentry(config: SentryConfig): Promise<boolean> {
  if (initialized) return true;
  currentConfig = config;

  try {
    // 动态导入，避免 @sentry/vue 成为运行时必要依赖
    sentryModule = await import('@sentry/vue');
  } catch {
    console.warn(
      '[Monitor] @sentry/vue not installed; Sentry forwarding disabled. ' +
      'To enable: pnpm add @sentry/vue --filter @ydsz/monitor'
    );
    return false;
  }

  const { init, vueIntegration, browserTracingIntegration } = sentryModule;

  init({
    dsn: config.dsn,
    release: config.release,
    environment: config.environment,
    integrations: [
      vueIntegration(),
      config.tracesSampleRate
        ? browserTracingIntegration()
        : null,
    ].filter(Boolean),
    sampleRate: config.sampleRate ?? 1,
    tracesSampleRate: config.tracesSampleRate ?? 0,
    // 隐藏敏感信息
    beforeSend(event: any) {
      // 可以在这里做最后的脱敏
      if (event.request?.headers?.Authorization) {
        delete event.request.headers.Authorization;
      }
      return event;
    },
  });

  initialized = true;
  console.info('[Monitor] Sentry adapter initialized', {
    release: config.release,
    environment: config.environment,
  });
  return true;
}

/**
 * 将内部 ErrorReport 转发到 Sentry
 *
 * 由 error-monitor 的 forwarder 调用，把已标准化的错误格式转换为 Sentry 事件。
 *
 * @param report - 标准化错误报告
 */
export function captureError(report: ErrorReport): void {
  if (!initialized || !sentryModule) return;

  const { captureException, configureScope } = sentryModule;

  // 将错误映射为 Sentry 级别
  const level = report.type === 'resource' ? 'warning' : 'error';

  configureScope((scope: any) => {
    // 设置标签便于 Sentry dashboard 过滤
    scope.setTag('error.type', report.type);
    scope.setTag('error.sessionId', report.sessionId || 'unknown');
    scope.setTag('error.traceId', report.traceId || 'unknown');

    // 设置上下文
    scope.setContext('error_report', {
      type: report.type,
      filename: report.filename,
      lineno: report.lineno,
      colno: report.colno,
      route: report.url,
      timestamp: report.timestamp,
    });

    // 面包屑
    if (report.breadcrumbs?.length) {
      scope.setContext('breadcrumbs', {
        trail: report.breadcrumbs.slice(-10).map((b) => ({
          category: b.category,
          message: b.message,
          timestamp: b.timestamp,
        })),
      });
    }

    // 用户信息
    if (report.userId) {
      scope.setUser({ id: report.userId });
    }

    scope.setLevel(level);
  });

  // 构造 Error 对象以携带完整 stack
  const error = new Error(report.message);
  error.stack = report.stack;
  error.name = `Monitor${report.type.charAt(0).toUpperCase() + report.type.slice(1)}Error`;

  captureException(error);
}

/**
 * 手动上报一条消息到 Sentry
 */
export function captureMessage(message: string, level: 'info' | 'warning' | 'error' = 'info'): void {
  if (!initialized || !sentryModule) return;
  const { captureMessage: sentryCaptureMessage } = sentryModule;
  sentryCaptureMessage(message, level);
}

/**
 * 设置 Sentry 用户上下文
 */
export function sentrySetUser(user: { id: string; username?: string; email?: string } | null): void {
  if (!initialized || !sentryModule) return;
  const { configureScope } = sentryModule;
  configureScope((scope: any) => {
    scope.setUser(user);
  });
}

/**
 * 检查 Sentry 是否已初始化
 */
export function isSentryInitialized(): boolean {
  return initialized;
}

/**
 * 获取当前 Sentry 配置
 */
export function getSentryConfig(): SentryConfig | null {
  return currentConfig;
}
