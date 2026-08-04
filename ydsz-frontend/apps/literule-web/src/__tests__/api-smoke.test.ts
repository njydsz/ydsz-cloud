/**
 * literule-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\literule-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('literule-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('rule API 请求路径正确', async () => {
    const { getRulePageApi, createRuleApi } = await import('../api/rule');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getRulePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/rules/page');

    await createRuleApi({ ruleName: '规则A' });
    expect(String(mockPost.mock.calls[0][0])).toContain('/rules');
  });

  it('variable API 请求路径正确', async () => {
    const { getVariablePageApi } = await import('../api/variable');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getVariablePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/variable/page');
  });

  it('dsl API 请求路径正确', async () => {
    const { getDslPageApi } = await import('../api/dsl');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getDslPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/dsl/page');
  });
});
