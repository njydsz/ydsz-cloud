/**
 * nextwiki-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\nextwiki-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('nextwiki-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('file API 请求路径正确', async () => {
    const { getFilePageApi, createFileApi } = await import('../api/file');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getFilePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/files/page');

    await createFileApi({ fileName: '文档' });
    expect(String(mockPost.mock.calls[0][0])).toContain('/files');
  });

  it('share API 请求路径正确', async () => {
    const { getSharePageApi } = await import('../api/share');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getSharePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/share/page');
  });

  it('tag API 请求路径正确', async () => {
    const { getTagPageApi } = await import('../api/tag');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getTagPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/tag/page');
  });
});
