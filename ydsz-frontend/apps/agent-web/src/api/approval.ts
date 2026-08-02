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
    /** 审批单唯一 ID */
    id: string;
    /** 关联 Agent 的 ID */
    agentId: string;
    /** 请求类型编码（对应工具调用类型）*/
    requestType: string;
    /** 工具调用的请求内容（通常为 JSON 字符串）*/
    requestContent: string;
    /** 审批人 */
    approver: string;
    /** 审批状态编码（如 pending / approved / rejected）*/
    approvalStatus: string;
    /** 创建时间（ISO 字符串）*/
    createTime: string;
  }

  export interface ApprovalPageQuery {
    /** 页码，从 1 开始 */
    pageNum?: number;
    /** 每页条数 */
    pageSize?: number;
    /** 关联 Agent ID 筛选（可选）*/
    agentId?: string;
    /** 审批状态筛选（可选）*/
    approvalStatus?: string;
  }

  export interface ApprovalDTO {
    /** 关联 Agent 的 ID */
    agentId?: string;
    /** 请求类型编码 */
    requestType?: string;
    /** 工具调用的请求内容 */
    requestContent?: string;
    /** 审批人 */
    approver?: string;
  }
}

/**
 * 分页查询 Agent 工具调用审批单列表。
 *
 * @param params - 分页与筛选条件（页码、页大小、Agent、审批状态）
 * @returns 分页结果，含 total 与当前页 items 等元信息
 */
export function getApprovalPageApi(params: ApprovalApi.ApprovalPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ApprovalApi.ApprovalVO[];
  }>(`/api/v1/agent/approvals/page`, { params });
}

/**
 * 查询全部审批单列表（不分页）。
 *
 * @returns 审批单视图对象数组
 */
export function getApprovalListApi() {
  return requestClient.get<ApprovalApi.ApprovalVO[]>(`/api/v1/agent/approvals/list`);
}

/**
 * 根据 ID 查询单个审批单详情。
 *
 * @param id - 审批单唯一 ID
 * @returns 审批单视图对象
 */
export function getApprovalByIdApi(id: string) {
  return requestClient.get<ApprovalApi.ApprovalVO>(`/api/v1/agent/approvals/${id}`);
}

/**
 * 创建审批单（发起一次人工审批）。
 *
 * @param data - 创建参数（Agent、请求类型、请求内容、审批人）
 * @returns 新建记录的 ID
 */
export function createApprovalApi(data: ApprovalApi.ApprovalDTO) {
  return requestClient.post<string>(`/api/v1/agent/approvals`, data);
}

/**
 * 更新审批单（按 ID 全量更新）。
 *
 * @param data - 更新参数，需包含待更新记录的 ID
 * @returns 是否更新成功
 */
export function updateApprovalApi(data: ApprovalApi.ApprovalDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/approvals`, data);
}

/**
 * 删除审批单。
 *
 * @param id - 待删除的审批单 ID
 * @returns 是否删除成功
 */
export function deleteApprovalApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/approvals/${id}`);
}
