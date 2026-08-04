/**
 * use-excel-export 单元测试
 *
 * @path comm\effects\shared-business\src\composables\use-excel-export.test.ts
 * @author ydsz-team
 * @since 1.1.0
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';

// Mock xlsx 的 writeFile，避免真实写文件
vi.mock('xlsx', () => {
  const actual = new Proxy(
    {},
    {
      get: (_target, prop: string) => {
        if (prop === 'writeFile') {
          return vi.fn();
        }
        if (prop === 'utils') {
          return {
            aoa_to_sheet: vi.fn(() => ({})),
            book_new: vi.fn(() => ({})),
            book_append_sheet: vi.fn(),
          };
        }
        return undefined;
      },
    },
  );
  return { ...(actual as any) };
});

import { useExcelExport } from './use-excel-export';

describe('useExcelExport', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('exportExcel 调用 writeFile 生成 xlsx', async () => {
    const { exportExcel } = useExcelExport();
    const result = exportExcel({
      fileName: 'test-export',
      columns: [
        { title: '名称', key: 'name' },
        { title: '金额', key: 'amount', formatter: (r: any) => `¥${r.amount}` },
      ],
      data: [
        { name: 'A', amount: 100 },
        { name: 'B', amount: 200 },
      ],
    });
    expect(result).toBe(true);
    // 应调用 writeFile
    const xlsx = await import('xlsx');
    expect(xlsx.writeFile).toHaveBeenCalled();
  });

  it('formatter 函数应用于数据转换', async () => {
    const { exportExcel } = useExcelExport();
    exportExcel({
      fileName: 'f',
      columns: [{ title: '金额', key: 'amount', formatter: (r: any) => r.amount * 2 }],
      data: [{ amount: 5 }],
    });
    const xlsx = await import('xlsx');
    // aoa_to_sheet 应收到 [['金额'], [10]]
    expect(xlsx.utils.aoa_to_sheet).toHaveBeenCalledWith([
      ['金额'],
      [10],
    ]);
  });
});
