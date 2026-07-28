/**
 * 后端统一响应类型 — 与 BaseResponse 对齐
 *
 * 后端类：com.njydsz.common.core.response.BaseResponse
 */

/** 统一 API 返回结果 */
export interface BaseResponse<T = unknown> {
  /** 响应码，200 表示成功 */
  code: number;
  /** 响应消息 */
  msg: string;
  /** 响应数据 */
  data: T;
  /** 响应时间戳 */
  timestamp: string;
  /** 异常信息（仅错误时存在） */
  info?: {
    code: string;
    key: string;
    message: string;
    httpStatus: number;
    path?: string;
    traceId?: string;
  };
}

/** 分页返回结果 — 与 PageResponse 对齐 */
export interface PageResponse<T = unknown> extends BaseResponse<PageData<T>> {}

/** 分页数据 */
export interface PageData<T = unknown> {
  /** 当前页数据列表 */
  list: T[];
  /** 总记录数 */
  total: number;
  /** 当前页码 */
  pageNum: number;
  /** 每页大小 */
  pageSize: number;
  /** 总页数 */
  pages: number;
}

/** 分页查询参数 — 与 PageQuery 对齐 */
export interface PageQuery {
  pageNum: number;
  pageSize: number;
  orderBy?: string;
  orderDirection?: 'asc' | 'desc';
}

/** 成功响应快捷判断 */
export function isSuccess<T>(resp: BaseResponse<T>): resp is BaseResponse<T> & { data: T } {
  return resp.code === 200;
}

/** 提取响应数据的快捷方法 */
export function unwrapResponse<T>(resp: BaseResponse<T>): T {
  if (resp.code !== 200) {
    throw new Error(resp.msg || 'Unknown error');
  }
  return resp.data;
}
