import { requestClient } from '#/api/request';

export namespace AgentApi {
  export interface AgentVO {
    id: string;
    agentName: string;
    agentType: string;
    modelProvider: string;
    modelName: string;
    systemPrompt: string;
    temperature: number;
    status: number;
    createTime: string;
  }

  export interface AgentPageQuery {
    pageNum?: number;
    pageSize?: number;
    agentName?: string;
  }

  export interface AgentDTO {
    agentName?: string;
    agentType?: string;
    modelProvider?: string;
    modelName?: string;
    systemPrompt?: string;
    temperature?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getAgentPageApi(params: AgentApi.AgentPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: AgentApi.AgentVO[];
  }>(`/api/v1/agent/page`, { params });
}

/** 查询全部列表 */
export function getAgentListApi() {
  return requestClient.get<AgentApi.AgentVO[]>(`/api/v1/agent/list`);
}

/** 根据 ID 查询 */
export function getAgentByIdApi(id: string) {
  return requestClient.get<AgentApi.AgentVO>(`/api/v1/agent/${id}`);
}

/** 创建 */
export function createAgentApi(data: AgentApi.AgentDTO) {
  return requestClient.post<string>(`/api/v1/agent`, data);
}

/** 更新 */
export function updateAgentApi(data: AgentApi.AgentDTO) {
  return requestClient.put<boolean>(`/api/v1/agent`, data);
}

/** 删除 */
export function deleteAgentApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/${id}`);
}
