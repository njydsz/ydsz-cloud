/**
 * 项目预算 API 模块（前端）
 * <p>封装项目预算（{@code ydsz_project_budget}）CRUD 接口，对应后端 {@code /api/v1/project/budget/*} 端点。
 * <p>支持科目维度（人力/材料/差旅/外采）预算编制、调整、执行分析。
 * <p>供「项目管理 → 预算管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace BudgetApi {
  /** 项目预算科目视图对象 */
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

  /** 预算科目分页查询参数 */
  export interface BudgetPageQuery {
    pageNum?: number;
    pageSize?: number;
    budgetItemName?: string;
    projectId?: string;
  }

  /** 预算科目创建/更新请求参数 */
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
