/**
 * @file WBS 任务管理页面 API 集成测试
 * @description 批次 26 P2-20。验证 WBS 任务页面依赖的 API 模块导出完整，
 *   且分页/增删改接口 URL 与 HTTP Method 与后端 WbsTaskController 契约一致。
 *   采用 mock @/utils/request + 导入真实 API 模块的方式（同 api/__tests__/agent.test.ts 约定），
 *   既验证导出存在，又验证 URL 契约，避免 "mock 自身" 的空转测试。
 * @module views/execution/__tests__/wbs-task
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock HTTP 层，拦截真实请求
vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import * as wbsTaskApi from '@/api/execution/wbs-task'
import { request } from '@/utils/request'

const requestMock = request as unknown as ReturnType<typeof vi.fn>

describe('WBS Task 页面 API (批次 26 P2-20)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API 模块应导出完整的增删改查方法', () => {
    expect(typeof wbsTaskApi.pageWbsTasks).toBe('function')
    expect(typeof wbsTaskApi.getWbsTask).toBe('function')
    expect(typeof wbsTaskApi.createWbsTask).toBe('function')
    expect(typeof wbsTaskApi.updateWbsTask).toBe('function')
    expect(typeof wbsTaskApi.changeWbsTaskStatus).toBe('function')
    expect(typeof wbsTaskApi.deleteWbsTask).toBe('function')
  })

  it('pageWbsTasks 应调用 GET /execution/wbs-task/page', () => {
    requestMock.mockReturnValue({ data: { list: [], total: 0 } })
    wbsTaskApi.pageWbsTasks(1, 20)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/wbs-task/page')
    expect(call.method).toBe('GET')
    expect(call.params.page).toBe(1)
    expect(call.params.size).toBe(20)
  })

  it('createWbsTask 应调用 POST /execution/wbs-task', () => {
    requestMock.mockReturnValue({ data: 1 })
    wbsTaskApi.createWbsTask({ initiationId: 1, name: '需求分析' } as any)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/wbs-task')
    expect(call.method).toBe('POST')
  })

  it('deleteWbsTask 应调用 DELETE /execution/wbs-task/{id}', () => {
    requestMock.mockReturnValue({ data: undefined })
    wbsTaskApi.deleteWbsTask(10)
    const call = requestMock.mock.calls[0][0]
    expect(call.url).toBe('/execution/wbs-task/10')
    expect(call.method).toBe('DELETE')
  })
})
