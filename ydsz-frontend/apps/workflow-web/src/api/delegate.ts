/**
 * 流程委托 API 模块（前端）
 * <p>封装流程委托（{@code ydsz_flow_delegate}）CRUD 接口，对应后端 {@code /api/v1/workflow/delegate/*} 端点。
 * <p>支持审批权限临时委托给同事（出差/请假场景）。
 * <p>供「工作流 → 我的委托」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  }>(`/api/v1/workflow/engine/page`, { params });
}

/** 查询全部列表 */
export function getDelegateListApi() {
  return requestClient.get<DelegateApi.DelegateVO[]>(`/api/v1/workflow/engine/list`);
}

/** 根据 ID 查询 */
export function getDelegateByIdApi(id: string) {
  return requestClient.get<DelegateApi.DelegateVO>(`/api/v1/workflow/engine/${id}`);
}

/** 创建 */
export function createDelegateApi(data: DelegateApi.DelegateDTO) {
  return requestClient.post<string>(`/api/v1/workflow/engine`, data);
}

/** 更新 */
export function updateDelegateApi(data: DelegateApi.DelegateDTO) {
  return requestClient.put<boolean>(`/api/v1/workflow/engine`, data);
}

/** 删除 */
export function deleteDelegateApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/workflow/engine/${id}`);
}
