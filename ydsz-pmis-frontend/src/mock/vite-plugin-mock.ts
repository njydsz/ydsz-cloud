/**
 * Vite 插件: 本地 Mock 数据服务器（批次 20 补齐 P1 vite-plugin-mock）
 *
 * 设计目标:
 * 1. 后端未启动时, 前端可独立运行 (开发联调、UI 调试、CI 截图测试)
 * 2. 不需要真实后端, 降低新成员 onboarding 成本
 * 3. 与 Vite dev server 集成, 不污染 build 产物
 *
 * 实现:
 * - 拦截 /mock-api/* 请求, 路由到 mock/handlers/ 下的处理器
 * - 默认所有模块都 mock, 关闭方式: env.VITE_USE_MOCK=false
 * - mock 响应延迟可配置 (env.VITE_MOCK_DELAY)
 */
import { Plugin } from 'vite'
import { mockHandlers } from './handlers'

export interface MockPluginOptions {
  /** 启用 mock 模式, 默认 true */
  enabled?: boolean
  /** 模拟网络延迟 (ms), 默认 200ms */
  delay?: number
  /** mock 前缀, 默认 /mock-api */
  prefix?: string
}

export function viteMockPlugin(options: MockPluginOptions = {}): Plugin {
  const { enabled = true, delay = 200, prefix = '/mock-api' } = options

  return {
    name: 'vite-plugin-pmis-mock',
    apply: 'serve',
    configureServer(server) {
      if (!enabled) {
        return
      }

      server.middlewares.use(`${prefix}`, async (req, res, next) => {
        // 仅处理 GET / POST
        if (req.method !== 'GET' && req.method !== 'POST') {
          return next()
        }

        const url = req.url || ''
        // 解析路径与查询参数
        const [pathname, queryString] = url.split('?')
        const query: Record<string, string> = {}
        if (queryString) {
          for (const pair of queryString.split('&')) {
            const [k, v] = pair.split('=')
            if (k) query[decodeURIComponent(k)] = decodeURIComponent(v || '')
          }
        }

        // 匹配 handler
        const handler = mockHandlers.find((h) => h.method === req.method && h.path === pathname)
        if (!handler) {
          res.statusCode = 404
          res.setHeader('Content-Type', 'application/json;charset=UTF-8')
          res.end(JSON.stringify({ code: 404, message: `Mock handler not found: ${req.method} ${pathname}` }))
          return
        }

        // 模拟延迟
        if (delay > 0) {
          await new Promise((r) => setTimeout(r, delay))
        }

        try {
          const body: unknown = req.method === 'POST' ? await readBody(req) : null
          const result = await handler.handler({ query, body })
          res.statusCode = 200
          res.setHeader('Content-Type', 'application/json;charset=UTF-8')
          res.setHeader('X-Mock-Source', 'vite-plugin-pmis-mock')
          res.end(JSON.stringify({ code: 0, message: 'success', data: result, timestamp: Date.now() }))
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

function readBody(req: any): Promise<unknown> {
  return new Promise((resolve) => {
    const chunks: Buffer[] = []
    req.on('data', (chunk: Buffer) => chunks.push(chunk))
    req.on('end', () => {
      try {
        const text = Buffer.concat(chunks).toString('utf-8')
        resolve(text ? JSON.parse(text) : null)
      } catch {
        resolve(null)
      }
    })
  })
}
