/**
 * 消息路由规则 API 模块（前端）
 * <p>封装消息路由规则接口，对应后端 {@code /api/v1/message/routeRule/*} 端点。
 * <p>支持按租户/部门/用户/优先级动态路由到不同渠道。
 * <p>供「消息中心 → 路由配置」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace RouteRuleApi {
  /** 消息路由规则视图对象 */
  export interface RouteRuleVO {
    id: string;
    ruleName: string;
    channel: string;
    priority: number;
    condition: string;
    targetChannel: string;
    status: number;
    createTime: string;
  }

  /** 路由规则分页查询参数 */
  export interface RouteRulePageQuery {
    pageNum?: number;
    pageSize?: number;
    ruleName?: string;
  }

  /** 路由规则创建/更新请求参数 */
  export interface RouteRuleDTO {
    ruleName?: string;
    channel?: string;
    priority?: number;
    condition?: string;
    targetChannel?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getRouteRulePageApi(params: RouteRuleApi.RouteRulePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RouteRuleApi.RouteRuleVO[];
  }>(`/api/v1/message/routeRule/page`, { params });
}

/** 查询全部列表 */
export function getRouteRuleListApi() {
  return requestClient.get<RouteRuleApi.RouteRuleVO[]>(`/api/v1/message/routeRule/list`);
}

/** 根据 ID 查询 */
export function getRouteRuleByIdApi(id: string) {
  return requestClient.get<RouteRuleApi.RouteRuleVO>(`/api/v1/message/routeRule/${id}`);
}

/** 创建 */
export function createRouteRuleApi(data: RouteRuleApi.RouteRuleDTO) {
  return requestClient.post<string>(`/api/v1/message/routeRule`, data);
}

/** 更新 */
export function updateRouteRuleApi(data: RouteRuleApi.RouteRuleDTO) {
  return requestClient.put<boolean>(`/api/v1/message/routeRule`, data);
}

/** 删除 */
export function deleteRouteRuleApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/message/routeRule/${id}`);
}
