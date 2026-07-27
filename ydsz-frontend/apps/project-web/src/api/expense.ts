import { requestClient } from '#/api/request';

export namespace ExpenseApi {
  export interface ExpenseVO {
    id: string;
    projectId: string;
    expenseType: string;
    amount: number;
    expenseDate: string;
    applicant: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface ExpensePageQuery {
    pageNum?: number;
    pageSize?: number;
    expenseType?: string;
    projectId?: string;
  }

  export interface ExpenseDTO {
    projectId?: string;
    expenseType?: string;
    amount?: number;
    expenseDate?: string;
    applicant?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getExpensePageApi(params: ExpenseApi.ExpensePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ExpenseApi.ExpenseVO[];
  }>(`/api/v1/project/project/expense/page`, { params });
}

/** 查询全部列表 */
export function getExpenseListApi() {
  return requestClient.get<ExpenseApi.ExpenseVO[]>(`/api/v1/project/project/expense/list`);
}

/** 根据 ID 查询 */
export function getExpenseByIdApi(id: string) {
  return requestClient.get<ExpenseApi.ExpenseVO>(`/api/v1/project/project/expense/${id}`);
}

/** 创建 */
export function createExpenseApi(data: ExpenseApi.ExpenseDTO) {
  return requestClient.post<string>(`/api/v1/project/project/expense`, data);
}

/** 更新 */
export function updateExpenseApi(data: ExpenseApi.ExpenseDTO) {
  return requestClient.put<boolean>(`/api/v1/project/project/expense`, data);
}

/** 删除 */
export function deleteExpenseApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/project/expense/${id}`);
}
