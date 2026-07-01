/**
 * @file viteMockPlugin 单元测试（批次 20 P1-3）
 * @description 验证 Vite Mock 中间件插件：
 *   - 路由匹配: method+path 精确命中
 *   - 未匹配时 fallback 到下一个中间件
 *   - 响应格式 (code=0/message/data/timestamp/traceId)
 *   - 多种 HTTP method 支持
 *   - 未启用时直接 passthrough
 * @module mock/__tests__/vite-plugin-mock
 */
import { describe, it, expect, vi } from 'vitest'
import { viteMockPlugin } from '../vite-plugin-mock'

type MockReq = {
  method?: string
  url?: string
  on: (event: string, cb: (chunk?: Buffer) => void) => void
  _endEmitted?: boolean
}
type MockRes = {
  statusCode: number
  setHeader: ReturnType<typeof vi.fn>
  end: ReturnType<typeof vi.fn>
}

interface ServerCapture {
  middlewares: Array<(r: any, s: any, n: any) => Promise<void> | void>
}

function captureConfigure(plugin: ReturnType<typeof viteMockPlugin>): ServerCapture {
  const cap: ServerCapture = { middlewares: [] }
  const server: any = {
    middlewares: {
      use: (...handlers: Array<(r: any, s: any, n: any) => void>) => {
        cap.middlewares.push(...handlers)
      },
    },
    config: { logger: { info: () => {} } },
  }
  ;(plugin.configureServer as (s: any) => void)(server)
  return cap
}

function makeReq(method: string, url: string, bodyText = ''): MockReq {
  // 立即注册 data/end 监听器, 并同步触发 end (没有 body)
  const req: MockReq = {
    method,
    url,
    on: (event, cb) => {
      if (event === 'data' && bodyText) {
        cb(Buffer.from(bodyText))
      } else if (event === 'end') {
        cb()
      }
    },
  }
  return req
}

function makeRes() {
  let captured = ''
  const res: MockRes = {
    statusCode: 200,
    setHeader: vi.fn(),
    end: vi.fn((data: string) => {
      captured = data
    }),
  }
  return { res, getBody: () => captured }
}

/** 同步运行中间件链, 直到某个中间件不再调用 next (命中) */
async function runChain(cap: ServerCapture, req: MockReq, res: MockRes) {
  let nextCalled = false
  const next = () => {
    nextCalled = true
  }
  for (const mw of cap.middlewares) {
    nextCalled = false
    await mw(req, res, next)
    if (!nextCalled) return
  }
}

describe('vite-plugin-pmis-mock', () => {
  it('未启用时不挂载中间件', () => {
    const plugin = viteMockPlugin({ enabled: false })
    const cap = captureConfigure(plugin)
    expect(cap.middlewares.length).toBe(0)
  })

  it('启用时挂载中间件', () => {
    const plugin = viteMockPlugin({ enabled: true })
    const cap = captureConfigure(plugin)
    expect(cap.middlewares.length).toBeGreaterThan(0)
  })

  it('匹配 GET /api/v1/auth/captcha 返回 captcha 数据', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('GET', '/api/v1/auth/captcha')
    const { res, getBody } = makeRes()
    await runChain(cap, req, res)
    expect(res.statusCode).toBe(200)
    const body = JSON.parse(getBody())
    expect(body.code).toBe(0)
    expect(body.data).toHaveProperty('captchaImage')
  })

  it('匹配 POST /api/v1/auth/login 返回 token', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('POST', '/api/v1/auth/login')
    const { res, getBody } = makeRes()
    await runChain(cap, req, res)
    const body = JSON.parse(getBody())
    expect(body.code).toBe(0)
    expect(body.data).toHaveProperty('accessToken')
  })

  it('匹配 /api/v1/users/me 返回用户信息', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('GET', '/api/v1/users/me')
    const { res, getBody } = makeRes()
    await runChain(cap, req, res)
    const body = JSON.parse(getBody())
    expect(body.code).toBe(0)
    expect(body.data.username).toBe('admin')
  })

  it('未匹配 /api/v1 路径时调用 next() (fallback)', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('GET', '/api/v1/non-existent/abc')
    const { res } = makeRes()
    let nextCalled = false
    const next = () => {
      nextCalled = true
    }
    await cap.middlewares[0](req, res, next)
    expect(nextCalled).toBe(true)
  })

  it('非 /api/v1 前缀直接 passthrough', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('GET', '/some/other/path')
    const { res } = makeRes()
    let nextCalled = false
    const next = () => {
      nextCalled = true
    }
    await cap.middlewares[0](req, res, next)
    expect(nextCalled).toBe(true)
  })

  it('响应包含 traceId 和 timestamp', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('GET', '/api/v1/users/me')
    const { res, getBody } = makeRes()
    await runChain(cap, req, res)
    const body = JSON.parse(getBody())
    expect(body.traceId).toMatch(/^mock-/)
    expect(typeof body.timestamp).toBe('number')
  })

  it('设置 X-Mock-Source header', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('GET', '/api/v1/auth/captcha')
    const { res } = makeRes()
    await runChain(cap, req, res)
    expect(res.setHeader).toHaveBeenCalledWith('X-Mock-Source', 'vite-plugin-pmis-mock')
  })

  it('OPTIONS / PUT / PATCH 走 next (非 GET/POST/DELETE 由 plugin 透传)', async () => {
    const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
    const req = makeReq('OPTIONS', '/api/v1/auth/captcha')
    const { res } = makeRes()
    let nextCalled = false
    const next = () => {
      nextCalled = true
    }
    await cap.middlewares[0](req, res, next)
    expect(nextCalled).toBe(true)
  })

  it('支持路径参数匹配 /api/v1/.../{id}/... (批次 25 P1-6)', async () => {
    // 临时注册一个带路径参数的 handler
    const { mockHandlers } = await import('../handlers')
    mockHandlers.push({
      method: 'POST',
      path: '/__test__/{id}/action',
      handler: () => ({ ok: true, id: 42 }),
    })
    try {
      const cap = captureConfigure(viteMockPlugin({ enabled: true, delay: 0 }))
      const req = makeReq('POST', '/api/v1/__test__/42/action')
      const { res, getBody } = makeRes()
      await runChain(cap, req, res)
      const body = JSON.parse(getBody())
      expect(body.code).toBe(0)
      expect(body.data).toEqual({ ok: true, id: 42 })
    } finally {
      // 清理
      for (let i = mockHandlers.length - 1; i >= 0; i--) {
        if (mockHandlers[i].path === '/__test__/{id}/action') {
          mockHandlers.splice(i, 1)
        }
      }
    }
  })
})
