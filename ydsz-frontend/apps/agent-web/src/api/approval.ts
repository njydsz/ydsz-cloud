/**
 * Agent 审批 API 模块（前端）
 * <p>封装 Agent 工具调用的人工审批（Human-in-the-Loop）接口，对应后端 {@code /api/v1/agent/approval/*} 端点。
 * <p>提供审批单的查询、通过、驳回、转办能力，确保高风险工具调用有人工把关。
 * <p>供「Agent 运营 → 待我审批」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  }>(`/api/v1/agent/approvals/page`, { params });
}

/** 查询全部列表 */
export function getApprovalListApi() {
  return requestClient.get<ApprovalApi.ApprovalVO[]>(`/api/v1/agent/approvals/list`);
}

/** 根据 ID 查询 */
export function getApprovalByIdApi(id: string) {
  return requestClient.get<ApprovalApi.ApprovalVO>(`/api/v1/agent/approvals/${id}`);
}

/** 创建 */
export function createApprovalApi(data: ApprovalApi.ApprovalDTO) {
  return requestClient.post<string>(`/api/v1/agent/approvals`, data);
}

/** 更新 */
export function updateApprovalApi(data: ApprovalApi.ApprovalDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/approvals`, data);
}

/** 删除 */
export function deleteApprovalApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/approvals/${id}`);
}
