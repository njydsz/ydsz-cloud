import { requestClient } from '#/api/request';

export namespace BreakpointApi {
  export interface BreakpointVO {
    id: string;
    ruleCode: string;
    condition: string;
    hitCount: number;
    status: number;
    createTime: string;
  }

  export interface BreakpointPageQuery {
    pageNum?: number;
    pageSize?: number;
    ruleCode?: string;
  }

  export interface BreakpointDTO {
    ruleCode?: string;
    condition?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getBreakpointPageApi(params: BreakpointApi.BreakpointPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: BreakpointApi.BreakpointVO[];
  }>(`/ruleEngine/breakpoints/page`, { params });
}

/** 查询全部列表 */
export function getBreakpointListApi() {
  return requestClient.get<BreakpointApi.BreakpointVO[]>(`/ruleEngine/breakpoints/list`);
}

/** 根据 ID 查询 */
export function getBreakpointByIdApi(id: string) {
  return requestClient.get<BreakpointApi.BreakpointVO>(`/ruleEngine/breakpoints/${id}`);
}

/** 创建 */
export function createBreakpointApi(data: BreakpointApi.BreakpointDTO) {
  return requestClient.post<string>(`/ruleEngine/breakpoints`, data);
}

/** 更新 */
export function updateBreakpointApi(data: BreakpointApi.BreakpointDTO) {
  return requestClient.put<boolean>(`/ruleEngine/breakpoints`, data);
}

/** 删除 */
export function deleteBreakpointApi(id: string) {
  return requestClient.delete<boolean>(`/ruleEngine/breakpoints/${id}`);
}
