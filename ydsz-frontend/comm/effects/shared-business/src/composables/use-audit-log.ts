/**
 * use-audit-log 组合式函数 — 操作审计日志查询
 *
 * @path comm\effects\shared-business\src\composables\use-audit-log.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 封装审计日志查询逻辑，对接后端 /api/v1/audit/log/page 端点。
 * 各子应用可复用统一组件与查询 Hook，避免重复实现。
 */
import { computed, ref } from 'vue';

/** 审计日志条目 */
export interface AuditLogItem {
  id: string;
  /** 操作人用户名 */
  operator: string;
  /** 操作人姓名 */
  operatorName?: string;
  /** 操作模块 */
  module: string;
  /** 操作类型：CREATE/UPDATE/DELETE/EXPORT/LOGIN/OTHER */
  action: string;
  /** 操作描述 */
  description: string;
  /** 请求路径 */
  requestPath?: string;
  /** 客户端 IP */
  ip?: string;
  /** 操作结果：SUCCESS/FAILED */
  result?: string;
  /** 操作时间 */
  createTime: string;
  [key: string]: any;
}

/** 审计日志查询参数 */
export interface AuditLogQuery {
  pageNum: number;
  pageSize: number;
  operator?: string;
  module?: string;
  action?: string;
  startTime?: string;
  endTime?: string;
}

/** 分页结果 */
export interface AuditLogPageResult {
  items: AuditLogItem[];
  total: number;
}

/** 分页查询函数签名（由子应用注入后端实现） */
export type AuditLogFetcher = (query: AuditLogQuery) => Promise<AuditLogPageResult>;

/**
 * 审计日志查询 Hook
 *
 * @param fetcher - 后端查询函数（子应用注入）
 *
 * @example
 * ```ts
 * const audit = useAuditLog((query) =>
 *   requestClient.get('/api/v1/audit/log/page', { params: query }),
 * );
 * ```
 */
export function useAuditLog(fetcher: AuditLogFetcher) {
  const loading = ref(false);
  const items = ref<AuditLogItem[]>([]);
  const total = ref(0);
  const pageNum = ref(1);
  const pageSize = ref(10);
  const filters = ref<Omit<AuditLogQuery, 'pageNum' | 'pageSize'>>({});

  const pagination = computed(() => ({
    current: pageNum.value,
    pageSize: pageSize.value,
    total: total.value,
  }));

  async function fetchData() {
    loading.value = true;
    try {
      const result = await fetcher({
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        ...filters.value,
      });
      items.value = result.items;
      total.value = result.total;
    } finally {
      loading.value = false;
    }
  }

  function search(newFilters?: Omit<AuditLogQuery, 'pageNum' | 'pageSize'>) {
    if (newFilters) {
      filters.value = newFilters;
    }
    pageNum.value = 1;
    return fetchData();
  }

  function changePage(page: number) {
    pageNum.value = page;
    return fetchData();
  }

  function changePageSize(size: number) {
    pageSize.value = size;
    pageNum.value = 1;
    return fetchData();
  }

  function reset() {
    filters.value = {};
    pageNum.value = 1;
    return fetchData();
  }

  return {
    fetchData,
    filters,
    items,
    loading,
    pageNum,
    pageSize,
    pagination,
    reset,
    search,
    changePage,
    changePageSize,
    total,
  };
}
