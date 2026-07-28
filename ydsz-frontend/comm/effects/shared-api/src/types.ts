/**
 * 通用 API 类型定义 — 与后端 BaseResponse/PageResponse 对齐
 */

/** 基础实体 */
export interface BaseEntity {
  id: string;
  createTime?: string;
  updateTime?: string;
  createBy?: string;
  updateBy?: string;
  deleted?: boolean;
}

/** 分页查询参数 — 与后端 PageQuery 对齐 */
export interface PageQuery {
  pageNum: number;
  pageSize: number;
  orderBy?: string;
  orderDirection?: 'asc' | 'desc';
}

/** 分页返回结果 — 与后端 PageResponse 对齐 */
export interface PageResult<T> {
  list: T[];
  total: number;
  pageNum: number;
  pageSize: number;
  pages: number;
}

/** 统一返回结果 — 与后端 BaseResponse 对齐 */
export interface BaseResponse<T = unknown> {
  code: number;
  msg: string;
  data: T;
  timestamp: string;
}

/** 标准 CRUD API 接口 */
export interface CrudApi<T, Q = Partial<T>, D = Partial<T>> {
  getById: (id: string) => Promise<BaseResponse<T>>;
  page: (query: PageQuery & Q) => Promise<BaseResponse<PageResult<T>>>;
  list: (query?: Q) => Promise<BaseResponse<T[]>>;
  create: (data: D) => Promise<BaseResponse<T>>;
  update: (id: string, data: D) => Promise<BaseResponse<T>>;
  remove: (id: string) => Promise<BaseResponse<boolean>>;
  batchRemove: (ids: string[]) => Promise<BaseResponse<boolean>>;
}
