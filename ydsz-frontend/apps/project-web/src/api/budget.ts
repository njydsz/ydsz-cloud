import { requestClient } from '#/api/request';

export namespace BudgetApi {
  export interface BudgetVO {
    id: string;
    projectId: string;
    budgetItemName: string;
    budgetType: string;
    plannedAmount: number;
    actualAmount: number;
    variance: number;
    status: number;
    createTime: string;
  }

  export interface BudgetPageQuery {
    pageNum?: number;
    pageSize?: number;
    budgetItemName?: string;
    projectId?: string;
  }

  export interface BudgetDTO {
    projectId?: string;
    budgetItemName?: string;
    budgetType?: string;
    plannedAmount?: number;
    actualAmount?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getBudgetPageApi(params: BudgetApi.BudgetPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: BudgetApi.BudgetVO[];
  }>(`/api/v1/project/project/budget/item/page`, { params });
}

/** 查询全部列表 */
export function getBudgetListApi() {
  return requestClient.get<BudgetApi.BudgetVO[]>(`/api/v1/project/project/budget/item/list`);
}

/** 根据 ID 查询 */
export function getBudgetByIdApi(id: string) {
  return requestClient.get<BudgetApi.BudgetVO>(`/api/v1/project/project/budget/item/${id}`);
}

/** 创建 */
export function createBudgetApi(data: BudgetApi.BudgetDTO) {
  return requestClient.post<string>(`/api/v1/project/project/budget/item`, data);
}

/** 更新 */
export function updateBudgetApi(data: BudgetApi.BudgetDTO) {
  return requestClient.put<boolean>(`/api/v1/project/project/budget/item`, data);
}

/** 删除 */
export function deleteBudgetApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/project/budget/item/${id}`);
}
