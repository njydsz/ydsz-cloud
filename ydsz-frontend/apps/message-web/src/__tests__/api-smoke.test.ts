/**
 * message-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\message-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mockGet = vi.fn();
const mockPost = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: { delete: vi.fn(), get: mockGet, post: mockPost, put: vi.fn() },
}));

describe('message-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('message API 请求路径正确', async () => {
    const { getMessagePageApi, createMessageApi } = await import('../api/message');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id');

    await getMessagePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/message/page');

    await createMessageApi({ title: '通知' });
    expect(String(mockPost.mock.calls[0][0])).toContain('/message');
  });

  it('notification API 请求路径正确', async () => {
    const { getNotificationPageApi } = await import('../api/notification');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getNotificationPageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/notification/page');
  });

  it('template API 请求路径正确', async () => {
    const { getTemplatePageApi } = await import('../api/template');
    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getTemplatePageApi({ pageNum: 1, pageSize: 10 });
    expect(String(mockGet.mock.calls[0][0])).toContain('/template/page');
  });
});
