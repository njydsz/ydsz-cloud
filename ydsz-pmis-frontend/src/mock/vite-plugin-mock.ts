/**
 * @file Vite 插件: 本地 Mock 数据服务器（批次 20 补齐 P1 vite-plugin-mock）
 * @description 在 Vite dev server 中拦截 /api/v1/* 请求并返回 Mock 数据,
 *              使前端在后端未启动时也能独立运行; 关闭后请求透传至 proxy。
 *
 * 设计目标:
 * 1. 后端未启动时, 前端可独立运行 (开发联调、UI 调试、CI 截图测试)
 * 2. 不需要真实后端, 降低新成员 onboarding 成本
 * 3. 与 Vite dev server 集成, 不污染 build 产物
 * 4. 拦截真实路径 /api/v1/*, 与生产环境完全一致, 关闭时自动 fallback 到 proxy
 *
 * 路由策略:
 * - 启用 mock: 直接在 dev server 中间件里响应 /api/v1/* 请求
 * - 关闭 mock: 请求原样透传给 proxy, 由 vite.config.ts 中的 proxy 配置转发
 *
 * 控制:
 * - 启用: env.VITE_USE_MOCK=true
 * - 关闭: env.VITE_USE_MOCK=false
 * - 模拟延迟: env.VITE_MOCK_DELAY (ms, 默认 200)
 *
 * @module mock/vite-plugin-mock
 */
import { Plugin } from 'vite'
import { mockHandlers } from './handlers'

export interface MockPluginOptions {
  /** 启用 mock 模式, 默认从 env.VITE_USE_MOCK 读取 */
  enabled?: boolean
  /** 模拟网络延迟 (ms), 默认 200ms */
  delay?: number
  /** mock 前缀, 默认 /api/v1 (与生产后端一致, 前端代码 0 改动) */
  prefix?: string
  /** 是否打印 mock 请求日志, 默认 false */
  verbose?: boolean
}

/**
 * Vite 插件工厂: 注册 PMIS 本地 Mock 中间件
 *
 * 在 dev server 中拦截 /api/v1/* 请求, 按方法+路径匹配 mockHandlers,
 * 命中则注入延迟并返回统一响应结构, 未命中则透传给 proxy。
 *
 * @param options 插件配置项 (enabled/delay/prefix/verbose), 缺省时从 env 读取
 * @returns Vite Plugin 实例 (仅在 serve 阶段生效)
 */
export function viteMockPlugin(options: MockPluginOptions = {}): Plugin {
  const {
    enabled = (import.meta.env?.VITE_USE_MOCK ?? 'true') === 'true',
    delay: defaultDelay = Number(import.meta.env?.VITE_MOCK_DELAY ?? 200),
    prefix = '/api/v1',
    verbose = false,
  } = options

  return {
    name: 'vite-plugin-pmis-mock',
    apply: 'serve',
    configureServer(server) {
      if (!enabled) {
        if (verbose) {
          server.config.logger.info('[mock] disabled, requests will go to proxy')
        }
        return
      }

      const delay = defaultDelay
      const logger = server.config.logger

      // 移除前缀, 拿到与后端 controller 一致的路径
      const stripPrefix = (url: string): string => {
        if (url.startsWith(prefix + '/') || url === prefix) {
          return url.slice(prefix.length) || '/'
        }
        return url
      }

      server.middlewares.use(async (req, res, next) => {
        const rawUrl = req.url || ''
        // 只接管 /api/v1 开头
        if (!rawUrl.startsWith('/api/v1')) {
          return next()
        }

        // 仅处理 GET / POST / PUT / DELETE / PATCH
        const method = (req.method || 'GET').toUpperCase()
        if (!['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
          return next()
        }

        const [pathWithQuery, queryString] = rawUrl.split('?')
        const path = stripPrefix(pathWithQuery)
        const query: Record<string, string> = {}
        if (queryString) {
          for (const pair of queryString.split('&')) {
            const [k, v] = pair.split('=')
            if (k) query[decodeURIComponent(k)] = decodeURIComponent(v || '')
          }
        }

        // 匹配 handler (path 已剥离 prefix, 与 handler.path 一致)
        // 支持路径参数匹配: /project/initiation/{id}/submit
        const handler = mockHandlers.find(
          (h) => h.method === method && matchPath(h.path, path),
        )
        if (!handler) {
          // 未匹配, fallback 到 proxy
          if (verbose) {
            logger.info(`[mock] miss ${method} ${path} -> proxy`)
          }
          return next()
        }

        // 模拟延迟
        if (delay > 0) {
          await new Promise((r) => setTimeout(r, delay))
        }

        try {
          const body: unknown =
            method === 'GET' || method === 'DELETE' ? null : await readBody(req)

          if (verbose) {
            logger.info(`[mock] ${method} ${path} hit`)
          }

          const result = await handler.handler({ query, body })
          res.statusCode = 200
          res.setHeader('Content-Type', 'application/json;charset=UTF-8')
          res.setHeader('X-Mock-Source', 'vite-plugin-pmis-mock')
          res.end(
            JSON.stringify({
              code: 0,
              message: 'success',
              data: result,
              traceId: `mock-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
              timestamp: Date.now(),
            }),
          )
        } catch (err) {
          res.statusCode = 500
          res.setHeader('Content-Type', 'application/json;charset=UTF-8')
          res.end(
            JSON.stringify({
              code: 500,
              message: err instanceof Error ? err.message : 'Mock handler error',
              timestamp: Date.now(),
            }),
          )
        }
      })
    },
  }
}

/**
 * 读取并解析请求体
 * @param req Node 原生 IncomingMessage
 * @returns Promise; 成功解析返回 JSON 对象, 解析失败或空 body 返回 null/原始字符串
 */
function readBody(req: any): Promise<unknown> {
  return new Promise((resolve) => {
    const chunks: Buffer[] = []
    req.on('data', (chunk: Buffer) => chunks.push(chunk))
    req.on('end', () => {
      const text = Buffer.concat(chunks).toString('utf-8')
      if (!text) {
        resolve(null)
        return
      }
      try {
        resolve(JSON.parse(text))
      } catch {
        resolve(text)
      }
    })
  })
}

/**
 * 路径匹配: 支持 {id} 等占位符.
 *   matchPath('/project/initiation/{id}/submit', '/project/initiation/42/submit') -> true
 *   matchPath('/auth/login', '/auth/login') -> true
 *   matchPath('/project/initiation/{id}/submit', '/project/initiation/42/cancel') -> false
 */
function matchPath(pattern: string, actual: string): boolean {
  if (pattern === actual) return true
  if (!pattern.includes('{')) return false
  const pParts = pattern.split('/')
  const aParts = actual.split('/')
  if (pParts.length !== aParts.length) return false
  for (let i = 0; i < pParts.length; i++) {
    const p = pParts[i]
    const a = aParts[i]
    if (p.startsWith('{') && p.endsWith('}')) continue
    if (p !== a) return false
  }
  return true
}
