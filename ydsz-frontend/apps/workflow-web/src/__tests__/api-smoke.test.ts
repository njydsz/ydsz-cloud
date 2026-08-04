/**
 * workflow-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\workflow-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('workflow-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('task API 请求路径正确', async () => {
    const { getTaskPageApi, createTaskApi } = await import('../api/task');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getTaskPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/task/page');

    await createTaskApi({ taskName: '审批任务' });
    expect(String(mockPost.mock.calls[0][0])).toContain('/task');
  });

  it('instance API 请求路径正确', async () => {
    const { getInstancePageApi } = await import('../api/instance');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getInstancePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/instance/page');
  });

  it('template API 请求路径正确', async () => {
    const { getTemplatePageApi } = await import('../api/template');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getTemplatePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/template/page');
  });
});
