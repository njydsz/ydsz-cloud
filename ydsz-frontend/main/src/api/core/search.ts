/**
 * search API 接口定义
 *
 * @path main\src\api\core\search.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace SearchApi {
  /** 全局搜索请求参数。 */
  export interface SearchRequest {
    /** 搜索关键词 */
    keyword: string;
    /** 限定搜索的模块，可选 */
    module?: string;
    /** 页码，从 1 开始，可选 */
    pageNum?: number;
    /** 每页条数，可选 */
    pageSize?: number;
  }

  /** 搜索结果条目。 */
  export interface SearchResultItem {
    /** 结果唯一 ID */
    id: string;
    /** 结果标题 */
    title: string;
    /** 内容摘要 */
    snippet: string;
    /** 所属模块 */
    module: string;
    /** 结果类型（如 doc / user 等） */
    type: string;
    /** 跳转地址 */
    url: string;
    /** 高亮片段，可选 */
    highlight?: string;
    /** 相关度评分，可选 */
    score?: number;
  }

  /** 全局搜索分页响应。 */
  export interface SearchResponse {
    /** 总命中数 */
    total: number;
    /** 当前页码 */
    current: number;
    /** 每页条数 */
    size: number;
    /** 当前页结果列表 */
    items: SearchResultItem[];
    /** 搜索建议词，可选 */
    suggestions?: string[];
  }
}

/**
 * 执行全局搜索。
 *
 * @param data - 搜索请求（关键词、可选模块、分页）
 * @returns 搜索结果分页与建议词
 */
export function globalSearchApi(data: SearchApi.SearchRequest) {
  return requestClient.post<SearchApi.SearchResponse>(
    '/api/v1/search',
    data,
  );
}

/**
 * 获取搜索建议词。
 *
 * @param keyword - 当前输入的关键词
 * @returns 建议词数组
 */
export function searchSuggestApi(keyword: string) {
  return requestClient.get<string[]>('/api/v1/search/suggest', {
    params: { keyword },
  });
}
