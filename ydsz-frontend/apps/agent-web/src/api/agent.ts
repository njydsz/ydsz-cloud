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
    /** 智能体唯一 ID */
    id: string;
    /** 智能体名称 */
    agentName: string;
    /** 智能体类型编码（如 chat / workflow 等）*/
    agentType: string;
    /** 模型供应商（如 openai / ollama 等）*/
    modelProvider: string;
    /** 模型名称 */
    modelName: string;
    /** 系统提示词模板 */
    systemPrompt: string;
    /** 采样温度，控制生成随机性，取值 0~1 */
    temperature: number;
    /** 启用状态：0 禁用，1 启用 */
    status: number;
    /** 创建时间（ISO 字符串）*/
    createTime: string;
  }

  export interface AgentPageQuery {
    /** 页码，从 1 开始 */
    pageNum?: number;
    /** 每页条数 */
    pageSize?: number;
    /** 名称模糊匹配（可选）*/
    agentName?: string;
  }

  export interface AgentDTO {
    /** 智能体名称 */
    agentName?: string;
    /** 智能体类型编码 */
    agentType?: string;
    /** 模型供应商 */
    modelProvider?: string;
    /** 模型名称 */
    modelName?: string;
    /** 系统提示词模板 */
    systemPrompt?: string;
    /** 采样温度，控制生成随机性，取值 0~1 */
    temperature?: number;
    /** 启用状态：0 禁用，1 启用 */
    status?: number;
  }
}

/**
 * 分页查询 Agent 智能体列表。
 *
 * @param params - 分页与筛选条件（页码、页大小、名称模糊匹配）
 * @returns 分页结果，含 total 与当前页 items 等元信息
 */
export function getAgentPageApi(params: AgentApi.AgentPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: AgentApi.AgentVO[];
  }>(`/api/v1/agent/page`, { params });
}

/**
 * 查询全部 Agent 智能体列表（不分页）。
 *
 * @returns Agent 视图对象数组
 */
export function getAgentListApi() {
  return requestClient.get<AgentApi.AgentVO[]>(`/api/v1/agent/list`);
}

/**
 * 根据 ID 查询单个 Agent 智能体详情。
 *
 * @param id - Agent 唯一 ID
 * @returns Agent 视图对象
 */
export function getAgentByIdApi(id: string) {
  return requestClient.get<AgentApi.AgentVO>(`/api/v1/agent/${id}`);
}

/**
 * 创建 Agent 智能体。
 *
 * @param data - 创建参数（名称、模型、提示词等）
 * @returns 新建记录的 ID
 */
export function createAgentApi(data: AgentApi.AgentDTO) {
  return requestClient.post<string>(`/api/v1/agent`, data);
}

/**
 * 更新 Agent 智能体（按 ID 全量更新）。
 *
 * @param data - 更新参数，需包含待更新记录的 ID
 * @returns 是否更新成功
 */
export function updateAgentApi(data: AgentApi.AgentDTO) {
  return requestClient.put<boolean>(`/api/v1/agent`, data);
}

/**
 * 删除 Agent 智能体。
 *
 * @param id - 待删除的 Agent ID
 * @returns 是否删除成功
 */
export function deleteAgentApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/${id}`);
}
