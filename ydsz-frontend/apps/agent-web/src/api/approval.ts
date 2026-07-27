import { requestClient } from '#/api/request';

export namespace ApprovalApi {
  export interface ApprovalVO {
    id: string;
    agentId: string;
    requestType: string;
    requestContent: string;
    approver: string;
    approvalStatus: string;
    createTime: string;
  }

  export interface ApprovalPageQuery {
    pageNum?: number;
    pageSize?: number;
    agentId?: string;
    approvalStatus?: string;
  }

  export interface ApprovalDTO {
    agentId?: string;
    requestType?: string;
    requestContent?: string;
    approver?: string;
  }
}

/** 分页查询 */
export function getApprovalPageApi(params: ApprovalApi.ApprovalPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ApprovalApi.ApprovalVO[];
  }>(`/agent/approvals/page`, { params });
}

/** 查询全部列表 */
export function getApprovalListApi() {
  return requestClient.get<ApprovalApi.ApprovalVO[]>(`/agent/approvals/list`);
}

/** 根据 ID 查询 */
export function getApprovalByIdApi(id: string) {
  return requestClient.get<ApprovalApi.ApprovalVO>(`/agent/approvals/${id}`);
}

/** 创建 */
export function createApprovalApi(data: ApprovalApi.ApprovalDTO) {
  return requestClient.post<string>(`/agent/approvals`, data);
}

/** 更新 */
export function updateApprovalApi(data: ApprovalApi.ApprovalDTO) {
  return requestClient.put<boolean>(`/agent/approvals`, data);
}

/** 删除 */
export function deleteApprovalApi(id: string) {
  return requestClient.delete<boolean>(`/agent/approvals/${id}`);
}
