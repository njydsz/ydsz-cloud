/**
 * cronjob-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\cronjob-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('cronjob-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('job API 请求路径正确', async () => {
    const { getJobPageApi, createJobApi } = await import('../api/job');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getJobPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/cronjob/page');

    await createJobApi({ jobName: 'sync' });
    expect(String(mockPost.mock.calls[0][0])).toContain('/cronjob');
  });

  it('jobGroup API 请求路径正确', async () => {
    const { getJobGroupPageApi } = await import('../api/jobGroup');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getJobGroupPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/group/page');
  });

  it('alert API 请求路径正确', async () => {
    const { getAlertPageApi } = await import('../api/alert');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getAlertPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/alert/page');
  });
});
