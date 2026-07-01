/**
 * @file 路由表结构 单元测试（批次18 增量，批次25 P0-1 适配）
 * @description 业务路由已迁移至 asyncRoutes（避免静态路由绕过权限），constantRoutes 仅保留
 *              login/404/dashboard/profile/cockpit/catch-all。验证 /report 父子路由结构、
 *              keepAlive 配置、meta.title 非空及批次18 权限码常量。
 * @module router/__tests__/routes-batch18
 * @author ydsz-pmis-team
 * @since 1.0.0 (批次18, 批次25 P0-1 适配)
 */
import { describe, it, expect } from 'vitest'
import { asyncRoutes } from '@/router/routes'

describe('routes 路由表结构（批次18 增量）', () => {
  function findRoute(fullPath: string): any | undefined {
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

  function findParent(path: string): any | undefined {
    return asyncRoutes.find((r) => r.path === path)
  }

  it('/report 父路由应存在', () => {
    const r = findParent('/report')
    expect(r).toBeDefined()
    expect(r?.meta?.title).toBe('报表中心')
  })

  it('/report 默认重定向到 /report/index', () => {
    const r = findParent('/report')
    expect(r?.redirect).toBe('/report/index')
  })

  it('/report 包含 index 与 executive 两个子路由', () => {
    const r = findParent('/report')
    const childPaths = (r?.children || []).map((c: any) => c.path)
    expect(childPaths).toContain('index')
    expect(childPaths).toContain('executive')
  })

  it('/report/executive 路由指向 executive 页面', () => {
    const r = findRoute('/report/executive')
    expect(r).toBeDefined()
    expect(r?.name).toBe('ReportExecutive')
    expect(r?.meta?.title).toBe('高管看板')
    expect(typeof r?.component).toBe('function')
  })

  it('/report/executive 子路由启用 keepAlive 以支持返回不刷新', () => {
    const r = findRoute('/report/executive')
    expect(r?.meta?.keepAlive).toBe(true)
  })

  it('/report 子路由 meta.title 非空', () => {
    const r = findParent('/report')
    for (const c of (r?.children || []) as any[]) {
      expect(c.meta?.title).toBeTruthy()
      expect(c.meta.title.length).toBeGreaterThan(0)
    }
  })
})

/**
 * 权限码测试（批次18 增量）
 */
describe('权限码 (批次18 增量)', () => {
  it('PC.REPORT_EXECUTIVE_VIEW 应等于 report:executive:view', async () => {
    const { PC } = await import('@/constants/permissionCodes')
    expect(PC.REPORT_EXECUTIVE_VIEW).toBe('report:executive:view')
  })

  it('PC.COCKPIT_ALERT_VIEW 应等于 cockpit:alert:view', async () => {
    const { PC } = await import('@/constants/permissionCodes')
    expect(PC.COCKPIT_ALERT_VIEW).toBe('cockpit:alert:view')
  })
})
