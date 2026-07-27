import { requestClient } from '#/api/request';

export namespace DefinitionApi {
  export interface DefinitionVO {
    id: string;
    defName: string;
    defCode: string;
    agentType: string;
    config: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface DefinitionPageQuery {
    pageNum?: number;
    pageSize?: number;
    defName?: string;
  }

  export interface DefinitionDTO {
    defName?: string;
    defCode?: string;
    agentType?: string;
    config?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getDefinitionPageApi(params: DefinitionApi.DefinitionPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DefinitionApi.DefinitionVO[];
  }>(`/agent/definitions/page`, { params });
}

/** 查询全部列表 */
export function getDefinitionListApi() {
  return requestClient.get<DefinitionApi.DefinitionVO[]>(`/agent/definitions/list`);
}

/** 根据 ID 查询 */
export function getDefinitionByIdApi(id: string) {
  return requestClient.get<DefinitionApi.DefinitionVO>(`/agent/definitions/${id}`);
}

/** 创建 */
export function createDefinitionApi(data: DefinitionApi.DefinitionDTO) {
  return requestClient.post<string>(`/agent/definitions`, data);
}

/** 更新 */
export function updateDefinitionApi(data: DefinitionApi.DefinitionDTO) {
  return requestClient.put<boolean>(`/agent/definitions`, data);
}

/** 删除 */
export function deleteDefinitionApi(id: string) {
  return requestClient.delete<boolean>(`/agent/definitions/${id}`);
}
