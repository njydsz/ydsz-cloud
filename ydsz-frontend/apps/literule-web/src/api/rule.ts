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
  }>(`/ruleEngine/rules/page`, { params });
}

/** 查询全部列表 */
export function getRuleListApi() {
  return requestClient.get<RuleApi.RuleVO[]>(`/ruleEngine/rules/list`);
}

/** 根据 ID 查询 */
export function getRuleByIdApi(id: string) {
  return requestClient.get<RuleApi.RuleVO>(`/ruleEngine/rules/${id}`);
}

/** 创建 */
export function createRuleApi(data: RuleApi.RuleDTO) {
  return requestClient.post<string>(`/ruleEngine/rules`, data);
}

/** 更新 */
export function updateRuleApi(data: RuleApi.RuleDTO) {
  return requestClient.put<boolean>(`/ruleEngine/rules`, data);
}

/** 删除 */
export function deleteRuleApi(id: string) {
  return requestClient.delete<boolean>(`/ruleEngine/rules/${id}`);
}
