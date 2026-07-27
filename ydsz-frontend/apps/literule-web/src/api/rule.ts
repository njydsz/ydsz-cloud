import { requestClient } from '#/api/request';

export namespace RuleApi {
  export interface RuleVO {
    id: string;
    ruleCode: string;
    ruleName: string;
    ruleType: string;
    priority: number;
    description: string;
    status: number;
    version: string;
    createTime: string;
  }

  export interface RulePageQuery {
    pageNum?: number;
    pageSize?: number;
    ruleName?: string;
    ruleCode?: string;
  }

  export interface RuleDTO {
    ruleCode?: string;
    ruleName?: string;
    ruleType?: string;
    priority?: number;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getRulePageApi(params: RuleApi.RulePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RuleApi.RuleVO[];
  }>(`/api/v1/literule/rules/page`, { params });
}

/** 查询全部列表 */
export function getRuleListApi() {
  return requestClient.get<RuleApi.RuleVO[]>(`/api/v1/literule/rules/list`);
}

/** 根据 ID 查询 */
export function getRuleByIdApi(id: string) {
  return requestClient.get<RuleApi.RuleVO>(`/api/v1/literule/rules/${id}`);
}

/** 创建 */
export function createRuleApi(data: RuleApi.RuleDTO) {
  return requestClient.post<string>(`/api/v1/literule/rules`, data);
}

/** 更新 */
export function updateRuleApi(data: RuleApi.RuleDTO) {
  return requestClient.put<boolean>(`/api/v1/literule/rules`, data);
}

/** 删除 */
export function deleteRuleApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/literule/rules/${id}`);
}
