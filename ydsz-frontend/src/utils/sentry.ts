/**
 * @file sentry Sentry 错误监控配置工具
 * @description Sentry 错误监控前端集成 (批次 20 P2-1)。
 *              设计目标:
 *                1. 错误自动上报 (uncaught exception, unhandled rejection, Vue errorHandler)
 *                2. 性能监控 (路由切换耗时 + API 请求耗时)
 *                3. 用户上下文 (登录后绑定 userId, 登出后清理)
 *                4. Source Map 自动上传 (Vite plugin @sentry/vite-plugin)
 *                5. 仅生产环境生效, dev 环境跳过 (避免开发噪音)
 *                6. 动态加载 @sentry/* 包, 减少 bundle 体积
 *              用法:
 *                import { initSentry, captureError, setUser } from '@/utils/sentry'
 *                if (import.meta.env.PROD) initSentry()
 *                setUser({ id: 1, username: 'admin' })
 * @module utils/sentry
 */
import type { App } from 'vue'

export interface SentryConfig {
  /** Sentry DSN, 例如 https://key@sentry.io/123 */
  dsn: string
  /** 部署环境 (production / staging / dev) */
  environment: string
  /** 发布版本 (CI_COMMIT_SHORT_SHA 或 package.json version) */
  release?: string
  /** 采样率 0-1, 默认 0.1 (10% 性能事件) */
  tracesSampleRate?: number
  /** 错误采样率 0-1, 默认 1.0 (100% 错误) */
  sampleRate?: number
  /** 会话回放采样率 0-1, 默认 0.1 (10% 会话) */
  replaysSessionSampleRate?: number
  /** 错误时会话回放采样率 0-1, 默认 1.0 (100% 错误会话) */
  replaysOnErrorSampleRate?: number
  /** 启用 Vue 集成, 默认 true */
  vueIntegration?: boolean
  /** 启用路由性能, 默认 true */
  routerIntegration?: boolean
  /** 启用 HTTP 性能, 默认 true */
  httpIntegration?: boolean
  /** 调试模式, 默认 false */
  debug?: boolean
  /** 错误前过滤, 返回 null 丢弃 */
  beforeSend?: (event: unknown) => unknown | null
}

/** Sentry 是否已初始化，避免重复 init */
let _initialized = false
/** 动态加载的 @sentry/vue 模块引用，类型用弱化签名以兼容动态 import */
let _sentryModule: Record<string, (...args: unknown[]) => unknown> | null = null

/**
 * 动态初始化 Sentry
 *
 * @param config Sentry 配置
 * @param app Vue 应用实例，传入后启用 Vue 集成
 * @param router Vue Router 实例，传入后启用路由性能监控
 * @returns Promise<void>，初始化完成或因 DSN 缺失/已初始化而跳过
 */
export async function initSentry(
  config: SentryConfig,
  app?: App,
  router?: { beforeEach: (cb: (to: unknown, from: unknown) => void) => void; afterEach: (cb: (to: unknown) => void) => void; onError: (cb: (err: unknown) => void) => void },
): Promise<void> {
  if (_initialized) return
  if (!config.dsn) {
    // eslint-disable-next-line no-console
    console.warn('[sentry] DSN 未配置, 跳过初始化')
    return
  }

  try {
    // 动态 import 减少主 bundle 体积
    // vite-ignore 让 Vite 不要静态扫描 @sentry/* 包
    // eslint-disable-next-line @typescript-eslint/no-implied-eval
    const dynamicImport = new Function('m', 'return import(m)') as (m: string) => Promise<Record<string, unknown>>
    const Sentry = (await dynamicImport(/* @vite-ignore */ '@sentry/vue')) as Record<string, (...args: unknown[]) => unknown>
    _sentryModule = Sentry

    const integrations: Record<string, unknown>[] = []

    if (config.httpIntegration !== false) {
      try {
        const browserMod = (await dynamicImport(/* @vite-ignore */ '@sentry/browser')) as Record<string, unknown>
        if (browserMod.BrowserTracing) {
          integrations.push({ browserTracing: browserMod.BrowserTracing })
        }
      } catch {
        // 浏览器版本不可用时跳过
      }
    }

    const initOptions: Record<string, unknown> = {
      app,
      dsn: config.dsn,
      environment: config.environment,
      release: config.release,
      tracesSampleRate: config.tracesSampleRate ?? 0.1,
      sampleRate: config.sampleRate ?? 1.0,
      replaysSessionSampleRate: config.replaysSessionSampleRate ?? 0.1,
      replaysOnErrorSampleRate: config.replaysOnErrorSampleRate ?? 1.0,
      debug: config.debug ?? false,
    }
    if (integrations.length > 0) {
      initOptions.integrations = integrations
    }
    initOptions.beforeSend = (event: unknown) => {
      // 过滤掉业务预期的 401/404 等
      const ex = (event as { exception?: { values?: { value?: string }[] } }).exception?.values?.[0]
      if (ex?.value?.includes('401')) return null
      if (ex?.value?.includes('404') && !config.debug) return null
      return config.beforeSend?.(event) ?? event
    }

    Sentry.init(initOptions)

    // 路由性能监控
    if (router && config.routerIntegration !== false) {
      router.beforeEach((_to, _from) => {
        // 路由切换开始
      })
      router.afterEach((to) => {
        Sentry.captureMessage?.(`route:${(to as { name?: string }).name ?? 'unknown'}`, 'info')
      })
      router.onError((err) => {
        Sentry.captureException(err)
      })
    }

    _initialized = true
    // eslint-disable-next-line no-console
    console.info('[sentry] initialized', { env: config.environment, release: config.release })
  } catch (e) {
    // Sentry 加载失败不能阻塞主应用
    // eslint-disable-next-line no-console
    console.error('[sentry] 初始化失败, 降级到 console.error', e)
  }
}

/**
 * 手动上报错误
 * @param err 错误对象或错误信息
 * @param context 附加上下文信息，将作为 extra 字段一并上报
 * @returns void，未初始化时降级到 console.error 输出
 */
export function captureError(err: unknown, context?: Record<string, unknown>): void {
  if (!_initialized || !_sentryModule) {
    // eslint-disable-next-line no-console
    console.error('[fallback]', err, context)
    return
  }
  if (context) {
    _sentryModule.captureException(err, { extra: context })
  } else {
    _sentryModule.captureException(err)
  }
}

/**
 * 绑定用户上下文
 * @param user 用户信息对象，传入 null 表示清理用户上下文（登出场景）
 * @returns void，未初始化时不执行任何操作
 */
export function setUser(user: { id: string | number; username?: string; email?: string } | null): void {
  if (!_initialized || !_sentryModule) return
  if (user === null) {
    _sentryModule.setUser(null)
  } else {
    _sentryModule.setUser({
      id: String(user.id),
      username: user.username,
      email: user.email,
    })
  }
}

/**
 * 添加面包屑
 * @param category 面包屑分类
 * @param message 面包屑消息
 * @param data 附加数据键值对
 * @returns void，未初始化时不执行任何操作
 */
export function addBreadcrumb(category: string, message: string, data?: Record<string, unknown>): void {
  if (!_initialized || !_sentryModule) return
  _sentryModule.addBreadcrumb({
    category,
    message,
    data: data as Record<string, string | number | boolean>,
    level: 'info',
  })
}

/**
 * 关闭 Sentry (登出后)
 * @returns void，未初始化时不执行任何操作
 */
export function closeSentry(): void {
  if (!_initialized || !_sentryModule) return
  _sentryModule.close()
  _initialized = false
  _sentryModule = null
}

/**
 * 上报性能测量数据
 * @param name 测量名称，如 'component.mount'
 * @param value 测量值（毫秒）
 * @param tags 附加标签
 * @returns void，未初始化时不执行任何操作
 */
export function captureMeasurement(name: string, value: number, tags?: Record<string, unknown>): void {
  if (!_initialized || !_sentryModule) return
  _sentryModule.addBreadcrumb?.({
    category: 'performance',
    message: name,
    data: { value: value.toFixed(2), ...tags } as Record<string, string | number | boolean>,
    level: 'info',
  })
}
