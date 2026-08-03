/**
 * 规则定义 API 模块（前端）
 * <p>封装规则定义（{@code ydsz_rule_def}）的 CRUD 接口调用，对应后端 {@code /api/v1/literule/rule/*} 端点。
 * <p>支持决策表、决策树、评分卡、脚本、复合规则等多种规则类型。
 * <p>供「规则引擎 → 规则管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace RuleApi {
  /** 规则定义视图对象 */
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

  /** 规则分页查询参数 */
  export interface RulePageQuery {
    pageNum?: number;
    pageSize?: number;
    ruleName?: string;
    ruleCode?: string;
  }

  /** 规则创建/更新请求参数 */
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
