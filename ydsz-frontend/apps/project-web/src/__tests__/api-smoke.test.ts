/**
 * project-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\project-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('project-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('contract API 请求路径正确', async () => {
    const { getContractPageApi, createContractApi } = await import('../api/contract');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getContractPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/contract/page');

    await createContractApi({ contractNo: 'HT-001' });
    expect(String(mockPost.mock.calls[0][0])).toContain('/contract');
  });

  it('budget API 请求路径正确', async () => {
    const { getBudgetPageApi } = await import('../api/budget');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getBudgetPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/budget/page');
  });

  it('opportunity API 请求路径正确', async () => {
    const { getOpportunityPageApi } = await import('../api/opportunity');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getOpportunityPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/opportunity/page');
  });
});
