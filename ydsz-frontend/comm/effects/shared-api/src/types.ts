/**
 * 通用 API 类型定义 — 从 @ydsz/types 统一导入，消除重复定义
 *
 * P2-4: 此前 @ydsz/shared-api 和 @ydsz/types 各自定义了一套 BaseResponse/PageQuery 等类型，
 * 现统一为 @ydsz/types 作为唯一来源，@ydsz/shared-api 仅保留 CrudApi（shared-api 特有）。
 */

// 从 @ydsz/types 统一导出（消除重复定义）
export type {
  BaseResponse,
  PageQuery,
  PageData,
  PageResponse,
  BaseEntity,
  TenantEntity,
  AuditEntity,
} from '@ydsz/types';

// 从 @ydsz/types 统一导出工具方法
export {
  isSuccess,
  unwrapResponse,
} from '@ydsz/types';

// PageResult 作为 PageData 的别名（向后兼容）
export type { PageData as PageResult } from '@ydsz/types';

/**
 * 标准 CRUD API 接口 — shared-api 特有类型
 */
export interface CrudApi<T, Q = Partial<T>, D = Partial<T>> {
  getById: (id: string) => Promise<BaseResponse<T>>;
  page: (query: PageQuery & Q) => Promise<BaseResponse<PageResult<T>>>;
  list: (query?: Q) => Promise<BaseResponse<T[]>>;
  create: (data: D) => Promise<BaseResponse<T>>;
  update: (id: string, data: D) => Promise<BaseResponse<T>>;
  remove: (id: string) => Promise<BaseResponse<boolean>>;
  batchRemove: (ids: string[]) => Promise<BaseResponse<boolean>>;
}

// 重新导入用于 CrudApi 类型引用
import type { BaseResponse, PageQuery, PageResult } from '@ydsz/types';
