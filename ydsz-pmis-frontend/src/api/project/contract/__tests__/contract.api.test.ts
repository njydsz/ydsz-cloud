/**
 * @file contract.api.test.ts
 * @description 测试合同管理 API 模块（CRUD 操作）
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock request 模块（使用 vi.hoisted 避免变量提升问题）
const { mockRequest } = vi.hoisted(() => ({ mockRequest: vi.fn() }))
vi.mock('@/utils/request', () => ({
  request: mockRequest,
}))

import {
  getContractListApi,
  getContractDetailApi,
  createContractApi,
  updateContractApi,
  deleteContractApi,
} from '@/api/project/contract'
import type { ContractQueryParams, ContractForm } from '@/api/project/contract'

describe('Contract API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getContractListApi', () => {
    it('应调用 GET /contracts 并传递分页参数', async () => {
      const params: ContractQueryParams = { page: 1, pageSize: 10, keyword: '测试合同' }
      mockRequest.mockResolvedValue({
        records: [{ id: 1, contractName: '测试合同' }],
        total: 1,
      })

      const result = await getContractListApi(params)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/contracts',
        method: 'GET',
        params: { page: 1, pageSize: 10, keyword: '测试合同' },
      })
      expect(result.records).toHaveLength(1)
      expect(result.total).toBe(1)
    })

    it('不传 keyword 时应正常查询', async () => {
      mockRequest.mockResolvedValue({ records: [], total: 0 })

      await getContractListApi({ page: 1, pageSize: 10 })

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/contracts',
        method: 'GET',
        params: { page: 1, pageSize: 10 },
      })
    })
  })

  describe('getContractDetailApi', () => {
    it('应调用 GET /contracts/:id 获取详情', async () => {
      mockRequest.mockResolvedValue({ id: 1, contractName: '合同详情', amount: 100000 })

      const result = await getContractDetailApi(1)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/contracts/1', method: 'GET' })
      expect(result.contractName).toBe('合同详情')
    })
  })

  describe('createContractApi', () => {
    it('应调用 POST /contracts 创建合同', async () => {
      const form: ContractForm = {
        contractName: '新合同',
        customerId: 1,
        amount: 500000,
        signDate: '2026-01-01',
      }
      mockRequest.mockResolvedValue({ id: 2, ...form })

      const result = await createContractApi(form)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/contracts', method: 'POST', data: form })
      expect(result.id).toBe(2)
    })
  })

  describe('updateContractApi', () => {
    it('应调用 PUT /contracts/:id 更新合同', async () => {
      const form: Partial<ContractForm> = { contractName: '更新后的合同' }
      mockRequest.mockResolvedValue({ id: 1, contractName: '更新后的合同' })

      const result = await updateContractApi(1, form)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/contracts/1', method: 'PUT', data: form })
      expect(result.contractName).toBe('更新后的合同')
    })
  })

  describe('deleteContractApi', () => {
    it('应调用 DELETE /contracts/:id 删除合同', async () => {
      mockRequest.mockResolvedValue(undefined)

      await deleteContractApi(1)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/contracts/1', method: 'DELETE' })
    })
  })
})