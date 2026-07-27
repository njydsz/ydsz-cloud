import { requestClient } from '#/api/request';

export namespace RevenueApi {
  export interface RevenueVO {
    id: string;
    projectId: string;
    contractId: string;
    revenueType: string;
    amount: number;
    revenueDate: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface RevenuePageQuery {
    pageNum?: number;
    pageSize?: number;
    revenueType?: string;
    projectId?: string;
  }

  export interface RevenueDTO {
    projectId?: string;
    contractId?: string;
    revenueType?: string;
    amount?: number;
    revenueDate?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getRevenuePageApi(params: RevenueApi.RevenuePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RevenueApi.RevenueVO[];
  }>(`/api/v1/project/project/revenue/page`, { params });
}

/** 查询全部列表 */
export function getRevenueListApi() {
  return requestClient.get<RevenueApi.RevenueVO[]>(`/api/v1/project/project/revenue/list`);
}

/** 根据 ID 查询 */
export function getRevenueByIdApi(id: string) {
  return requestClient.get<RevenueApi.RevenueVO>(`/api/v1/project/project/revenue/${id}`);
}

/** 创建 */
export function createRevenueApi(data: RevenueApi.RevenueDTO) {
  return requestClient.post<string>(`/api/v1/project/project/revenue`, data);
}

/** 更新 */
export function updateRevenueApi(data: RevenueApi.RevenueDTO) {
  return requestClient.put<boolean>(`/api/v1/project/project/revenue`, data);
}

/** 删除 */
export function deleteRevenueApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/project/revenue/${id}`);
}
