import { describe, it, expect } from 'vitest'
import { constantRoutes, asyncRoutes } from '@/router/routes'

/**
 * 路由表结构测试（批次25 P0-1 路由权限改造）
 *
 * 验证：
 *  - constantRoutes 仅包含基础路由（login/404/dashboard/profile/cockpit/catch-all）
 *  - 业务路由全部迁移到 asyncRoutes
 *  - 业务路由必须配置 meta.permCode
 *  - /agent /report 等关键子路由结构与 keepAlive
 *  - 所有完整路径不重复
 */
describe('routes 静态路由（constantRoutes）应仅含基础路由', () => {
  it('constantRoutes 顶级路径仅含 /login /404 / 和 catch-all', () => {
    const topPaths = constantRoutes.map((r) => r.path)
    expect(topPaths).toContain('/login')
    expect(topPaths).toContain('/404')
    expect(topPaths).toContain('/')
    expect(topPaths).toContain('/:pathMatch(.*)*')
    // 业务路由顶级路径不应出现在 constantRoutes
    expect(topPaths).not.toContain('/system')
    expect(topPaths).not.toContain('/project')
    expect(topPaths).not.toContain('/execution')
    expect(topPaths).not.toContain('/finance')
    expect(topPaths).not.toContain('/aftersales')
    expect(topPaths).not.toContain('/resource')
    expect(topPaths).not.toContain('/attendance')
    expect(topPaths).not.toContain('/report')
    expect(topPaths).not.toContain('/audit')
    expect(topPaths).not.toContain('/agent')
  })

  it('根布局下仅包含 dashboard / profile/security / cockpit 3 个基础页面', () => {
    const root = constantRoutes.find((r) => r.path === '/')
    expect(root).toBeDefined()
    const childPaths = (root?.children || []).map((c) => c.path)
    expect(childPaths).toEqual(
      expect.arrayContaining(['dashboard', 'profile/security', 'cockpit']),
    )
    expect(childPaths.length).toBe(3)
  })
})

describe('routes 动态路由（asyncRoutes）应包含全部业务路由', () => {
  function findAsyncRoute(fullPath: string): any | undefined {
    for (const r of asyncRoutes) {
      if (r.path === fullPath) return r
      if (r.children) {
        for (const c of r.children) {
          const composed = `${r.path}/${c.path}`.replace(/\/+/g, '/')
          if (composed === fullPath) return c
        }
      }
    }
    return undefined
  }

  function findAsyncParent(path: string): any | undefined {
    return asyncRoutes.find((r) => r.path === path)
  }

  it('asyncRoutes 应包含全部业务模块父路由', () => {
    const topPaths = asyncRoutes.map((r) => r.path)
    expect(topPaths).toContain('/system')
    expect(topPaths).toContain('/project')
    expect(topPaths).toContain('/execution')
    expect(topPaths).toContain('/finance')
    expect(topPaths).toContain('/aftersales')
    expect(topPaths).toContain('/resource')
    expect(topPaths).toContain('/attendance')
    expect(topPaths).toContain('/report')
    expect(topPaths).toContain('/audit')
    expect(topPaths).toContain('/agent')
  })

  it('/agent 父路由应存在', () => {
    const r = findAsyncParent('/agent')
    expect(r).toBeDefined()
    expect(r?.meta?.title).toBe('AI 智能体')
  })

  it('/agent 默认重定向到 /agent/orchestration', () => {
    const r = findAsyncParent('/agent')
    expect(r?.redirect).toBe('/agent/orchestration')
  })

  it('/agent 包含 orchestration 和 prediction 两个子路由', () => {
    const r = findAsyncParent('/agent')
    const childPaths = (r?.children || []).map((c: any) => c.path)
    expect(childPaths).toContain('orchestration')
    expect(childPaths).toContain('prediction')
  })

  it('/agent/orchestration 路由指向 orchestration 页面', () => {
    const r = findAsyncRoute('/agent/orchestration')
    expect(r).toBeDefined()
    expect(r?.name).toBe('AgentOrchestration')
    expect(r?.meta?.title).toBe('多智能体编排')
    expect(typeof r?.component).toBe('function')
  })

  it('/agent/prediction 路由指向 prediction 页面', () => {
    const r = findAsyncRoute('/agent/prediction')
    expect(r).toBeDefined()
    expect(r?.name).toBe('AgentPrediction')
    expect(r?.meta?.title).toBe('预测结果历史')
    expect(typeof r?.component).toBe('function')
  })

  it('/agent 子路由启用 keepAlive 以支持返回不刷新', () => {
    const r = findAsyncParent('/agent')
    for (const c of (r?.children || []) as any[]) {
      expect(c.meta?.keepAlive).toBe(true)
    }
  })

  it('/report 父路由应存在', () => {
    const r = findAsyncParent('/report')
    expect(r).toBeDefined()
    expect(r?.meta?.title).toBe('报表中心')
  })

  it('/report 默认重定向到 /report/index', () => {
    const r = findAsyncParent('/report')
    expect(r?.redirect).toBe('/report/index')
  })

  it('/report 包含 index 与 executive 两个子路由', () => {
    const r = findAsyncParent('/report')
    const childPaths = (r?.children || []).map((c: any) => c.path)
    expect(childPaths).toContain('index')
    expect(childPaths).toContain('executive')
  })

  it('/report/executive 路由指向 executive 页面', () => {
    const r = findAsyncRoute('/report/executive')
    expect(r).toBeDefined()
    expect(r?.name).toBe('ReportExecutive')
    expect(r?.meta?.title).toBe('高管看板')
    expect(typeof r?.component).toBe('function')
  })

  it('/report/executive 子路由启用 keepAlive', () => {
    const r = findAsyncRoute('/report/executive')
    expect(r?.meta?.keepAlive).toBe(true)
  })

  it('/report 子路由 meta.title 非空', () => {
    const r = findAsyncParent('/report')
    for (const c of (r?.children || []) as any[]) {
      expect(c.meta?.title).toBeTruthy()
    }
  })
})

describe('routes 业务路由权限码配置（P0-1 安全改造）', () => {
  /**
   * 递归收集所有叶子路由（含路径、permCode、title）
   */
  function collectLeaves(
    routes: any[],
    parentPath = '',
  ): { fullPath: string; permCode?: string; title?: string; name?: string }[] {
    const leaves: { fullPath: string; permCode?: string; title?: string; name?: string }[] = []
    for (const r of routes) {
      if (r.children && r.children.length > 0) {
        const next = r.path && !r.path.includes(':pathMatch')
          ? `${parentPath}/${r.path}`.replace(/\/+/g, '/').replace(/\/$/, '') || '/'
          : parentPath
        leaves.push(...collectLeaves(r.children, next))
      } else if (r.path) {
        const full = `${parentPath}/${r.path}`.replace(/\/+/g, '/').replace(/\/$/, '') || '/'
        leaves.push({
          fullPath: full,
          permCode: r.meta?.permCode,
          title: r.meta?.title,
          name: r.name,
        })
      }
    }
    return leaves
  }

  it('asyncRoutes 中所有叶子路由必须配置 meta.permCode', () => {
    const leaves = collectLeaves(asyncRoutes)
    expect(leaves.length).toBeGreaterThan(20) // 至少 20 个业务页面
    const missing = leaves.filter((l) => !l.permCode)
    expect(missing).toEqual([])
  })

  it('permCode 必须是三段式（含两个 : 分隔符）', () => {
    const leaves = collectLeaves(asyncRoutes)
    for (const l of leaves) {
      if (!l.permCode) continue
      // 形如 module:resource:action
      const segments = l.permCode.split(':')
      expect(segments.length).toBeGreaterThanOrEqual(3)
      expect(l.permCode).toMatch(/^[a-z][a-z-]*:[a-z][a-z-]*:[a-z-]+$/)
    }
  })

  it('所有路由 name 唯一', () => {
    const names = new Set<string>()
    function collect(rs: any[]) {
      for (const r of rs) {
        if (r.name) {
          const n = String(r.name)
          expect(names.has(n)).toBe(false)
          names.add(n)
        }
        if (r.children) collect(r.children)
      }
    }
    collect(asyncRoutes)
    expect(names.size).toBeGreaterThan(0)
  })

  it('所有完整路径不重复', () => {
    const allPaths = new Set<string>()
    function collect(rs: any[], parentPath = '') {
      for (const r of rs) {
        if (r.path) {
          if (r.path.includes(':pathMatch')) {
            allPaths.add(r.path)
          } else {
            const full = `${parentPath}/${r.path}`.replace(/\/+/g, '/').replace(/\/$/, '') || '/'
            expect(allPaths.has(full)).toBe(false)
            allPaths.add(full)
          }
        }
        if (r.children) {
          const parent = r.path && !r.path.includes(':pathMatch')
            ? `${parentPath}/${r.path}`.replace(/\/+/g, '/').replace(/\/$/, '')
            : parentPath
          collect(r.children, parent)
        }
      }
    }
    collect(asyncRoutes)
    expect(allPaths.size).toBeGreaterThan(0)
  })
})
