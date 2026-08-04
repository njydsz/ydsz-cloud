/**
 * agent-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\agent-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('agent-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('agent API 请求路径正确', async () => {
    const { getAgentPageApi, createAgentApi } = await import('../api/agent');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getAgentPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/agent/page');

    await createAgentApi({ agentName: '助手' });
    expect(String(mockPost.mock.calls[0][0])).toContain('/agent');
  });

  it('approval API 请求路径正确', async () => {
    const { getApprovalPageApi } = await import('../api/approval');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getApprovalPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/approval/page');
  });

  it('rag API 请求路径正确', async () => {
    const { getRagPageApi } = await import('../api/rag');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getRagPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/rag/page');
  });
});
