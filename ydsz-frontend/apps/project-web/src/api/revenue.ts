/**
 * 项目回款 API 模块（前端）
 * <p>封装项目回款（{@code ydsz_project_revenue}）CRUD 接口，对应后端 {@code /api/v1/project/revenue/*} 端点。
 * <p>记录合同回款节点、发票号、到账金额、到账日期。
 * <p>供「项目管理 → 回款管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace RevenueApi {
  /** 项目回款记录视图对象 */
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

  /** 回款记录分页查询参数 */
  export interface RevenuePageQuery {
    pageNum?: number;
    pageSize?: number;
    revenueType?: string;
    projectId?: string;
  }

  /** 回款记录创建/更新请求参数 */
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
