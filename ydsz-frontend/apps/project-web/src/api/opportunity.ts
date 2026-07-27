import { requestClient } from '#/api/request';

export namespace OpportunityApi {
  export interface OpportunityVO {
    id: string;
    opportunityName: string;
    customerName: string;
    opportunityType: string;
    estimatedAmount: number;
    stage: string;
    expectedCloseDate: string;
    salesPerson: string;
    status: number;
    createTime: string;
  }

  export interface OpportunityPageQuery {
    pageNum?: number;
    pageSize?: number;
    opportunityName?: string;
    stage?: string;
  }

  export interface OpportunityDTO {
    opportunityName?: string;
    customerName?: string;
    opportunityType?: string;
    estimatedAmount?: number;
    stage?: string;
    expectedCloseDate?: string;
    salesPerson?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getOpportunityPageApi(params: OpportunityApi.OpportunityPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: OpportunityApi.OpportunityVO[];
  }>(`/api/v1/project/project/opportunity/page`, { params });
}

/** 查询全部列表 */
export function getOpportunityListApi() {
  return requestClient.get<OpportunityApi.OpportunityVO[]>(`/api/v1/project/project/opportunity/list`);
}

/** 根据 ID 查询 */
export function getOpportunityByIdApi(id: string) {
  return requestClient.get<OpportunityApi.OpportunityVO>(`/api/v1/project/project/opportunity/${id}`);
}

/** 创建 */
export function createOpportunityApi(data: OpportunityApi.OpportunityDTO) {
  return requestClient.post<string>(`/api/v1/project/project/opportunity`, data);
}

/** 更新 */
export function updateOpportunityApi(data: OpportunityApi.OpportunityDTO) {
  return requestClient.put<boolean>(`/api/v1/project/project/opportunity`, data);
}

/** 删除 */
export function deleteOpportunityApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/project/opportunity/${id}`);
}
