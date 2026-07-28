/**
 * 项目风险 API 模块（前端）
 * <p>封装项目风险（{@code ydsz_project_risk}）CRUD 接口，对应后端 {@code /api/v1/project/risk/*} 端点。
 * <p>支持风险登记、概率/影响矩阵、应对措施、跟踪闭环。
 * <p>供「项目管理 → 风险管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace RiskApi {
  export interface RiskVO {
    id: string;
    projectId: string;
    riskName: string;
    riskType: string;
    probability: number;
    impact: number;
    riskLevel: string;
    mitigation: string;
    status: number;
    createTime: string;
  }

  export interface RiskPageQuery {
    pageNum?: number;
    pageSize?: number;
    riskName?: string;
  }

  export interface RiskDTO {
    projectId?: string;
    riskName?: string;
    riskType?: string;
    probability?: number;
    impact?: number;
    mitigation?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getRiskPageApi(params: RiskApi.RiskPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RiskApi.RiskVO[];
  }>(`/api/v1/project/execution/risk/page`, { params });
}

/** 查询全部列表 */
export function getRiskListApi() {
  return requestClient.get<RiskApi.RiskVO[]>(`/api/v1/project/execution/risk/list`);
}

/** 根据 ID 查询 */
export function getRiskByIdApi(id: string) {
  return requestClient.get<RiskApi.RiskVO>(`/api/v1/project/execution/risk/${id}`);
}

/** 创建 */
export function createRiskApi(data: RiskApi.RiskDTO) {
  return requestClient.post<string>(`/api/v1/project/execution/risk`, data);
}

/** 更新 */
export function updateRiskApi(data: RiskApi.RiskDTO) {
  return requestClient.put<boolean>(`/api/v1/project/execution/risk`, data);
}

/** 删除 */
export function deleteRiskApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/execution/risk/${id}`);
}
