import { requestClient } from '#/api/request';

export namespace RouteRuleApi {
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

  export interface RouteRulePageQuery {
    pageNum?: number;
    pageSize?: number;
    ruleName?: string;
  }

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
  }>(`/message/routeRule/page`, { params });
}

/** 查询全部列表 */
export function getRouteRuleListApi() {
  return requestClient.get<RouteRuleApi.RouteRuleVO[]>(`/message/routeRule/list`);
}

/** 根据 ID 查询 */
export function getRouteRuleByIdApi(id: string) {
  return requestClient.get<RouteRuleApi.RouteRuleVO>(`/message/routeRule/${id}`);
}

/** 创建 */
export function createRouteRuleApi(data: RouteRuleApi.RouteRuleDTO) {
  return requestClient.post<string>(`/message/routeRule`, data);
}

/** 更新 */
export function updateRouteRuleApi(data: RouteRuleApi.RouteRuleDTO) {
  return requestClient.put<boolean>(`/message/routeRule`, data);
}

/** 删除 */
export function deleteRouteRuleApi(id: string) {
  return requestClient.delete<boolean>(`/message/routeRule/${id}`);
}
