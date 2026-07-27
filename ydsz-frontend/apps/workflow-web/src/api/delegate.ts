import { requestClient } from '#/api/request';

export namespace DelegateApi {
  export interface DelegateVO {
    id: string;
    assignee: string;
    delegateTo: string;
    startDate: string;
    endDate: string;
    reason: string;
    status: number;
    createTime: string;
  }

  export interface DelegatePageQuery {
    pageNum?: number;
    pageSize?: number;
    assignee?: string;
  }

  export interface DelegateDTO {
    assignee?: string;
    delegateTo?: string;
    startDate?: string;
    endDate?: string;
    reason?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getDelegatePageApi(params: DelegateApi.DelegatePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DelegateApi.DelegateVO[];
  }>(`/workflow/engine/page`, { params });
}

/** 查询全部列表 */
export function getDelegateListApi() {
  return requestClient.get<DelegateApi.DelegateVO[]>(`/workflow/engine/list`);
}

/** 根据 ID 查询 */
export function getDelegateByIdApi(id: string) {
  return requestClient.get<DelegateApi.DelegateVO>(`/workflow/engine/${id}`);
}

/** 创建 */
export function createDelegateApi(data: DelegateApi.DelegateDTO) {
  return requestClient.post<string>(`/workflow/engine`, data);
}

/** 更新 */
export function updateDelegateApi(data: DelegateApi.DelegateDTO) {
  return requestClient.put<boolean>(`/workflow/engine`, data);
}

/** 删除 */
export function deleteDelegateApi(id: string) {
  return requestClient.delete<boolean>(`/workflow/engine/${id}`);
}
