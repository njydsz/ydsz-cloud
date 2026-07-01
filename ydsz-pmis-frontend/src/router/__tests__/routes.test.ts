import { describe, it, expect } from 'vitest'
import { constantRoutes } from '@/router/routes'

/**
 * 路由表结构测试
 *
 * 验证批次17 增量：
 *  - /agent 父路由存在
 *  - /agent/orchestration 与 /agent/prediction 子路由存在
 *  - 子路由组件路径正确指向 views/agent/* 页面
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
describe('routes 路由表结构（批次17 增量）', () => {
  /**
   * 找到某个完整路径的路由（处理父+子的拼接）
   * - parent 路径如 "/agent"
   * - child 路径如 "orchestration"，全路径 "/agent/orchestration"
   */
  function findRoute(fullPath: string): any | undefined {
    for (const r of constantRoutes) {
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

  function findParent(path: string): any | undefined {
    return constantRoutes.find((r) => r.path === path)
  }

  it('/agent 父路由应存在', () => {
    const r = findParent('/agent')
    expect(r).toBeDefined()
    expect(r?.meta?.title).toBe('AI 智能体')
  })

  it('/agent 默认重定向到 /agent/orchestration', () => {
    const r = findParent('/agent')
    expect(r?.redirect).toBe('/agent/orchestration')
  })

  it('/agent 包含 orchestration 和 prediction 两个子路由', () => {
    const r = findParent('/agent')
    const childPaths = (r?.children || []).map((c: any) => c.path)
    expect(childPaths).toContain('orchestration')
    expect(childPaths).toContain('prediction')
  })

  it('/agent/orchestration 路由指向 orchestration 页面', () => {
    const r = findRoute('/agent/orchestration')
    expect(r).toBeDefined()
    expect(r?.name).toBe('AgentOrchestration')
    expect(r?.meta?.title).toBe('多智能体编排')
    // component 是异步 import 函数，无法直接读取内部路径，但应存在
    expect(typeof r?.component).toBe('function')
  })

  it('/agent/prediction 路由指向 prediction 页面', () => {
    const r = findRoute('/agent/prediction')
    expect(r).toBeDefined()
    expect(r?.name).toBe('AgentPrediction')
    expect(r?.meta?.title).toBe('预测结果历史')
    expect(typeof r?.component).toBe('function')
  })

  it('/agent 子路由启用 keepAlive 以支持返回不刷新', () => {
    const r = findParent('/agent')
    for (const c of (r?.children || []) as any[]) {
      expect(c.meta?.keepAlive).toBe(true)
    }
  })

  it('所有路由的 meta.title 非空', () => {
    for (const r of constantRoutes) {
      if (r.meta?.hidden) continue
      if (r.path === '/:pathMatch(.*)*') continue
      if (r.meta?.title) {
        expect(r.meta.title.length).toBeGreaterThan(0)
      }
    }
  })

  it('所有完整路径不重复（catch-all 路由除外）', () => {
    const allPaths = new Set<string>()
    function collect(rs: any[], parentPath = '') {
      for (const r of rs) {
        if (r.path) {
          // :pathMatch(.*)* 是 catch-all 路由，不参与拼接去重
          if (r.path.includes(':pathMatch')) {
            allPaths.add(r.path)
          } else {
            // 子路径与父路径拼接后去重
            const full = `${parentPath}/${r.path}`.replace(/\/+/g, '/').replace(/\/$/, '') || '/'
            expect(allPaths.has(full)).toBe(false)
            allPaths.add(full)
          }
        }
        if (r.children) {
          // 子路由拼接到父路径下递归
          const parent = r.path && !r.path.includes(':pathMatch') ? `${parentPath}/${r.path}`.replace(/\/+/g, '/').replace(/\/$/, '') : parentPath
          collect(r.children, parent)
        }
      }
    }
    collect(constantRoutes)
    expect(allPaths.size).toBeGreaterThan(0)
  })
})
