/**
 * userinfo-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\userinfo-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('userinfo-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('user API 请求路径与方法正确', async () => {
    const { getUserPageApi, createUserApi } = await import('../api/user');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getUserPageApi({ pageNum: 1, pageSize: 10 });
    expect(mockGet).toHaveBeenCalledWith('/api/v1/user/page', { params: { pageNum: 1, pageSize: 10 } });

    await createUserApi({ username: 'admin' });
    expect(mockPost).toHaveBeenCalledWith('/api/v1/user', { username: 'admin' });
  });

  it('dept API 请求路径正确', async () => {
    const { getDeptListApi } = await import('../api/dept');
    mockGet.mockResolvedValue([]);
    await getDeptListApi();
    expect(mockGet).toHaveBeenCalled();
    expect(String(mockGet.mock.calls[0][0])).toContain('/dept/list');
  });

  it('role API 请求路径正确', async () => {
    const { getRolePageApi } = await import('../api/role');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getRolePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/role/page');
  });
});
