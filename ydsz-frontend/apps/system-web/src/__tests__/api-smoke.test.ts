/**
 * system-web 冒烟测试 — 核心 API 模块导出完整性
 *
 * @path apps\system-web\src\__tests__\api-smoke.test.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 验证核心 API 模块的函数导出与请求路径、方法正确性。
 * 使用 vi.mock 拦截 requestClient，避免真实网络请求。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Mock requestClient：捕获调用参数
const mockGet = vi.fn();
const mockPost = vi.fn();
const mockPut = vi.fn();
const mockDelete = vi.fn();

vi.mock('#/api/request', () => ({
  requestClient: {
    delete: mockDelete,
    get: mockGet,
    post: mockPost,
    put: mockPut,
  },
}));

describe('system-web API 冒烟测试', () => {
  beforeEach(() => {
    mockGet.mockReset();
    mockPost.mockReset();
    mockPut.mockReset();
    mockDelete.mockReset();
  });

  afterEach(() => {
    vi.resetModules();
  });

  it('dictType API 请求路径与方法正确', async () => {
    const { getDicttypePageApi, createDicttypeApi, deleteDicttypeApi } =
      await import('../api/dictType');

    mockGet.mockResolvedValue({ total: 0, items: [] });
    mockPost.mockResolvedValue('id-1');
    mockDelete.mockResolvedValue(true);

    await getDicttypePageApi({ pageNum: 1, pageSize: 10, typeName: '状态' });
    expect(mockGet).toHaveBeenCalledWith('/api/v1/dict/type/page', {
      params: { pageNum: 1, pageSize: 10, typeName: '状态' },
    });

    await createDicttypeApi({ typeCode: 'T1', typeName: '测试' });
    expect(mockPost).toHaveBeenCalledWith('/api/v1/dict/type', {
      typeCode: 'T1',
      typeName: '测试',
    });

    await deleteDicttypeApi('abc');
    expect(mockDelete).toHaveBeenCalledWith('/api/v1/dict/type/abc');
  });

  it('dictItem API 请求路径与方法正确', async () => {
    const { getDictItemListByTypeApi, getDictitemPageApi } = await import(
      '../api/dictItem'
    );

    mockGet.mockResolvedValue([]);

    await getDictitemPageApi({ typeCode: 'status' });
    expect(mockGet).toHaveBeenCalledWith('/api/v1/dict/item/page', {
      params: { typeCode: 'status' },
    });

    await getDictItemListByTypeApi('status');
    expect(mockGet).toHaveBeenCalledWith('/api/v1/dict/item/type/status');
  });

  it('config API 请求路径正确', async () => {
    const { getConfigPageApi } = await import('../api/config');

    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getConfigPageApi({ pageNum: 1, pageSize: 20 });
    expect(mockGet).toHaveBeenCalledWith('/api/v1/config/page', {
      params: { pageNum: 1, pageSize: 20 },
    });
  });

  it('app API 请求路径正确', async () => {
    const { getAppPageApi } = await import('../api/app');

    mockGet.mockResolvedValue({ total: 0, items: [] });
    await getAppPageApi({ pageNum: 1, pageSize: 10 });
    expect(mockGet).toHaveBeenCalled();
  });
});
