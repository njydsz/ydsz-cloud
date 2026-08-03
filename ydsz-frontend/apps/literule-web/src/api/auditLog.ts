/**
 * 规则审计日志 API 模块（前端）
 * <p>封装规则审计日志接口，对应后端 {@code /api/v1/literule/auditLog/*} 端点。
 * <p>记录规则的发布、版本变更、A/B 分流、灰度发布、回滚等关键事件。
 * <p>供「规则引擎 → 审计中心」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace AuditLogApi {
  /** 规则审计日志视图对象 */
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

  /** 审计日志分页查询参数 */
  export interface AuditLogPageQuery {
    pageNum?: number;
    pageSize?: number;
    ruleCode?: string;
  }

  /** 审计日志创建/更新请求参数 */
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
  }>(`/api/v1/literule/audit/page`, { params });
}

/** 查询全部列表 */
export function getAuditLogListApi() {
  return requestClient.get<AuditLogApi.AuditLogVO[]>(`/api/v1/literule/audit/list`);
}

/** 根据 ID 查询 */
export function getAuditLogByIdApi(id: string) {
  return requestClient.get<AuditLogApi.AuditLogVO>(`/api/v1/literule/audit/${id}`);
}

/** 创建 */
export function createAuditLogApi(data: AuditLogApi.AuditLogDTO) {
  return requestClient.post<string>(`/api/v1/literule/audit`, data);
}

/** 更新 */
export function updateAuditLogApi(data: AuditLogApi.AuditLogDTO) {
  return requestClient.put<boolean>(`/api/v1/literule/audit`, data);
}

/** 删除 */
export function deleteAuditLogApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/literule/audit/${id}`);
}
