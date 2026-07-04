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
  pageContracts,
  getContract,
  createContract,
  updateContract,
  deleteContract,
} from '@/api/project/contract'
import type { ContractCreateDTO } from '@/api/project/contract'

describe('Contract API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('pageContracts', () => {
    it('应调用 GET /project/contract/page 并传递分页参数', async () => {
      mockRequest.mockResolvedValue({
        records: [{ id: 1, contractName: '测试合同' }],
        total: 1,
      })

      const result = await pageContracts(1, 10, { keyword: '测试合同' })

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/project/contract/page',
        method: 'GET',
        params: { page: 1, size: 10, keyword: '测试合同' },
      })
      expect(result.records).toHaveLength(1)
      expect(result.total).toBe(1)
    })

    it('不传 keyword 时应正常查询', async () => {
      mockRequest.mockResolvedValue({ records: [], total: 0 })

      await pageContracts(1, 10)

      expect(mockRequest).toHaveBeenCalledWith({
        url: '/project/contract/page',
        method: 'GET',
        params: { page: 1, size: 10 },
      })
    })
  })

  describe('getContract', () => {
    it('应调用 GET /project/contract/1 获取详情', async () => {
      mockRequest.mockResolvedValue({ id: 1, contractName: '合同详情', amount: 100000 })

      const result = await getContract(1)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/project/contract/1', method: 'GET' })
      expect(result.contractName).toBe('合同详情')
    })
  })

  describe('createContract', () => {
    it('应调用 POST /project/contract 创建合同', async () => {
      const form: ContractCreateDTO = {
        contractName: '新合同',
        customerId: 1,
        amount: 500000,
        signDate: '2026-01-01',
      }
      mockRequest.mockResolvedValue({ id: 2, ...form })

      const result = await createContract(form)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/project/contract', method: 'POST', data: form })
      expect(result.id).toBe(2)
    })
  })

  describe('updateContract', () => {
    it('应调用 PUT /project/contract 更新合同', async () => {
      const form = { id: 1, contractName: '更新后的合同' }
      mockRequest.mockResolvedValue({ id: 1, contractName: '更新后的合同' })

      const result = await updateContract(form)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/project/contract', method: 'PUT', data: form })
      expect(result.contractName).toBe('更新后的合同')
    })
  })

  describe('deleteContract', () => {
    it('应调用 DELETE /project/contract/1 删除合同', async () => {
      mockRequest.mockResolvedValue(undefined)

      await deleteContract(1)

      expect(mockRequest).toHaveBeenCalledWith({ url: '/project/contract/1', method: 'DELETE' })
    })
  })
})