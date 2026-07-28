/**
 * search API 接口定义
 *
 * @path main\src\api\core\search.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace SearchApi {
  export interface SearchRequest {
    keyword: string;
    module?: string;
    pageNum?: number;
    pageSize?: number;
  }

  export interface SearchResultItem {
    id: string;
    title: string;
    snippet: string;
    module: string;
    type: string;
    url: string;
    highlight?: string;
    score?: number;
  }

  export interface SearchResponse {
    total: number;
    current: number;
    size: number;
    items: SearchResultItem[];
    suggestions?: string[];
  }
}

/** 全局搜索 */
export function globalSearchApi(data: SearchApi.SearchRequest) {
  return requestClient.post<SearchApi.SearchResponse>(
    '/api/v1/search',
    data,
  );
}

/** 搜索建议 */
export function searchSuggestApi(keyword: string) {
  return requestClient.get<string[]>('/api/v1/search/suggest', {
    params: { keyword },
  });
}
