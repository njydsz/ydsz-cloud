import { requestClient } from '#/api/request';

export namespace InitiationApi {
  export interface InitiationVO {
    id: string;
    projectCode: string;
    projectName: string;
    contractId: string;
    projectManager: string;
    projectType: string;
    startDate: string;
    endDate: string;
    totalBudget: number;
    status: number;
    createTime: string;
  }

  export interface InitiationPageQuery {
    pageNum?: number;
    pageSize?: number;
    projectName?: string;
    projectCode?: string;
  }

  export interface InitiationDTO {
    projectCode?: string;
    projectName?: string;
    contractId?: string;
    projectManager?: string;
    projectType?: string;
    startDate?: string;
    endDate?: string;
    totalBudget?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getInitiationPageApi(params: InitiationApi.InitiationPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: InitiationApi.InitiationVO[];
  }>(`/api/v1/project/initiation/page`, { params });
}

/** 查询全部列表 */
export function getInitiationListApi() {
  return requestClient.get<InitiationApi.InitiationVO[]>(`/api/v1/project/initiation/list`);
}

/** 根据 ID 查询 */
export function getInitiationByIdApi(id: string) {
  return requestClient.get<InitiationApi.InitiationVO>(`/api/v1/project/initiation/${id}`);
}

/** 创建 */
export function createInitiationApi(data: InitiationApi.InitiationDTO) {
  return requestClient.post<string>(`/api/v1/project/initiation`, data);
}

/** 更新 */
export function updateInitiationApi(data: InitiationApi.InitiationDTO) {
  return requestClient.put<boolean>(`/api/v1/project/initiation`, data);
}

/** 删除 */
export function deleteInitiationApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/initiation/${id}`);
}
