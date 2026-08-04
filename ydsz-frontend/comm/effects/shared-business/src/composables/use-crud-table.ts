/**
 * use-crud-table 组合式函数 — 通用 CRUD 列表页 Hook
 *
 * @path comm\effects\shared-business\src\composables\use-crud-table.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 整合服务端分页 + 删除确认 + 刷新 + 新增/编辑弹窗控制，
 * 对标 Vben Admin useTable 的能力子集，让标准 CRUD 页面少于 200 行。
 */
import { computed, ref } from 'vue';

import { ElMessage, ElMessageBox } from 'element-plus';

import {
  useServerPagination,
  type ServerPaginationFetcher,
} from './use-server-pagination';

/** 删除函数签名 */
export type DeleteFetcher<T = any> = (row: T) => Promise<unknown>;

/** CRUD 配置 */
export interface CrudTableOptions<T = any, Q = Record<string, any>> {
  /** 分页查询函数 */
  fetcher: ServerPaginationFetcher<T, Q>;
  /** 删除函数（可选，不传则不显示删除按钮） */
  deleteFetcher?: DeleteFetcher<T>;
  /** 删除确认文案 */
  deleteMessage?: (row: T) => string;
  /** 主键字段名，默认 id */
  rowKey?: string;
  /** 初始分页大小 */
  pageSize?: number;
}

/**
 * 通用 CRUD 列表 Hook
 *
 * @example
 * ```ts
 * const crud = useCrudTable({
 *   fetcher: (query) => getListApi(query),
 *   deleteFetcher: (row) => deleteApi(row.id),
 * });
 *
 * // 模板中：@confirm-delete="crud.handleDelete"
 * ```
 */
export function useCrudTable<T = any, Q = Record<string, any>>(
  options: CrudTableOptions<T, Q>,
) {
  const { fetcher, deleteFetcher, deleteMessage, rowKey = 'id', pageSize = 10 } = options;

  const {
    changePage,
    changePageSize,
    fetchData,
    items,
    loading,
    pagination,
    search,
    total,
  } = useServerPagination<T, Q>(fetcher, {} as Q, { pageSize });

  /** 弹窗状态 */
  const dialogVisible = ref(false);
  const editingRow = ref<T | null>(null);
  const isEdit = computed(() => editingRow.value !== null);

  /** 批量选择 */
  const selectedRows = ref<T[]>([]);

  /** 打开新增弹窗 */
  function openCreate() {
    editingRow.value = null;
    dialogVisible.value = true;
  }

  /** 打开编辑弹窗 */
  function openEdit(row: T) {
    editingRow.value = row;
    dialogVisible.value = true;
  }

  /** 关闭弹窗 */
  function closeDialog() {
    dialogVisible.value = false;
    editingRow.value = null;
  }

  /** 保存成功回调（关闭弹窗并刷新） */
  async function onSaved() {
    closeDialog();
    await fetchData();
  }

  /** 删除（带确认） */
  async function handleDelete(row: T) {
    if (!deleteFetcher) return;
    const msg =
      deleteMessage?.(row) ?? `确定要删除这条记录吗？删除后不可恢复。`;
    try {
      await ElMessageBox.confirm(msg, '删除确认', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      });
    } catch {
      return; // 用户取消
    }

    try {
      await deleteFetcher(row);
      ElMessage.success('删除成功');
      // 若当前页删空且不是第一页，回退一页
      if (items.value.length === 1 && pagination.value.current > 1) {
        changePage(pagination.value.current - 1);
      } else {
        await fetchData();
      }
    } catch (error) {
      ElMessage.error('删除失败');
      throw error;
    }
  }

  /** 批量删除 */
  async function handleBatchDelete() {
    if (!deleteFetcher || selectedRows.value.length === 0) return;
    try {
      await ElMessageBox.confirm(
        `确定要删除选中的 ${selectedRows.value.length} 条记录吗？`,
        '批量删除确认',
        { type: 'warning' },
      );
    } catch {
      return;
    }
    await Promise.all(selectedRows.value.map((row) => deleteFetcher(row)));
    ElMessage.success('批量删除成功');
    selectedRows.value = [];
    await fetchData();
  }

  return {
    changePage,
    changePageSize,
    closeDialog,
    dialogVisible,
    editingRow,
    fetchData,
    handleBatchDelete,
    handleDelete,
    isEdit,
    items,
    loading,
    onSaved,
    openCreate,
    openEdit,
    pagination,
    rowKey,
    search,
    selectedRows,
    total,
  };
}
