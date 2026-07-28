/**
 * Agent 智能体 API 模块（前端）
 * <p>封装 Agent 智能体定义的 CRUD 接口调用，对应后端 {@code /api/v1/agent/*} 端点。
 * <p>包含模型供应商/模型名称/系统提示词/温度等配置。
 * <p>供「Agent 管理 → 智能体列表」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
