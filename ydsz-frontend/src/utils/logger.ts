/**
 * @file 统一日志工具
 * @module utils/logger
 * @description 替代 console.* 的统一日志工具，支持级别控制和 Sentry 上报。
 *   - 开发环境：全量输出到控制台
 *   - 生产环境：warn/error 上报 Sentry，info/debug 静默
 *   - 所有日志自动附加 traceId（由 trace.ts 注入）
 */
import * as Sentry from '@sentry/vue'

/** 日志级别 */
export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
}

/** 当前环境最低日志级别 */
const minLevel: LogLevel = import.meta.env.PROD ? LogLevel.WARN : LogLevel.DEBUG

/**
 * 统一日志封装
 * - debug/info：仅开发环境输出到控制台
 * - warn：控制台输出 + Sentry breadcrumb
 * - error：控制台输出 + Sentry captureException
 */
export const logger = {
  /**
   * 调试日志（仅开发环境输出）
   * @param tag 模块标签，如 '[WorkflowMonitor]'
   * @param args 日志参数
   */
  debug(tag: string, ...args: unknown[]): void {
    if (minLevel <= LogLevel.DEBUG) {
      console.debug(tag, ...args)
    }
  },

  /**
   * 信息日志（仅开发环境输出）
   * @param tag 模块标签
   * @param args 日志参数
   */
  info(tag: string, ...args: unknown[]): void {
    if (minLevel <= LogLevel.INFO) {
      console.info(tag, ...args)
    }
  },

  /**
   * 警告日志（控制台 + Sentry breadcrumb）
   * @param tag 模块标签
   * @param args 日志参数
   */
  warn(tag: string, ...args: unknown[]): void {
    console.warn(tag, ...args)
    Sentry.addBreadcrumb({
      category: tag,
      level: 'warning',
      message: typeof args[0] === 'string' ? args[0] : JSON.stringify(args[0]),
      data: args.slice(1),
    })
  },

  /**
   * 错误日志（控制台 + Sentry captureException）
   * @param tag 模块标签
   * @param error 错误对象或消息
   * @param context 附加上下文
   */
  error(tag: string, error: unknown, context?: Record<string, unknown>): void {
    console.error(tag, error)
    if (error instanceof Error) {
      Sentry.captureException(error, {
        tags: { module: tag },
        extra: context,
      })
    } else {
      Sentry.captureMessage(`${tag} ${String(error)}`, 'error')
    }
  },
}
