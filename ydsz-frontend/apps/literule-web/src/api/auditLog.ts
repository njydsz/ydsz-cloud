import { requestClient } from '#/api/request';

export namespace AuditLogApi {
  export interface AuditLogVO {
    id: string;
    ruleCode: string;
    ruleName: string;
    triggerTime: string;
    result: string;
    duration: number;
    operator: string;
    createTime: string;
  }

  export interface AuditLogPageQuery {
    pageNum?: number;
    pageSize?: number;
    ruleCode?: string;
  }

  export interface AuditLogDTO {
    ruleCode?: string;
    operator?: string;
  }
}

/** 分页查询 */
export function getAuditLogPageApi(params: AuditLogApi.AuditLogPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: AuditLogApi.AuditLogVO[];
  }>(`/ruleEngine/audit/page`, { params });
}

/** 查询全部列表 */
export function getAuditLogListApi() {
  return requestClient.get<AuditLogApi.AuditLogVO[]>(`/ruleEngine/audit/list`);
}

/** 根据 ID 查询 */
export function getAuditLogByIdApi(id: string) {
  return requestClient.get<AuditLogApi.AuditLogVO>(`/ruleEngine/audit/${id}`);
}

/** 创建 */
export function createAuditLogApi(data: AuditLogApi.AuditLogDTO) {
  return requestClient.post<string>(`/ruleEngine/audit`, data);
}

/** 更新 */
export function updateAuditLogApi(data: AuditLogApi.AuditLogDTO) {
  return requestClient.put<boolean>(`/ruleEngine/audit`, data);
}

/** 删除 */
export function deleteAuditLogApi(id: string) {
  return requestClient.delete<boolean>(`/ruleEngine/audit/${id}`);
}
