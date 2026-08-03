/**
 * Agent 定义 API 模块（前端）
 * <p>封装 Agent 工具（Tool）注册与元数据维护接口，对应后端 {@code /api/v1/agent/definition/*} 端点。
 * <p>包含工具名称/描述/参数 Schema/调用方式等。
 * <p>供「Agent 工具市场」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace DefinitionApi {
  /** Agent 工具定义视图对象 */
  export interface DefinitionVO {
    /** 工具定义唯一 ID */
    id: string;
    /** 工具名称 */
    defName: string;
    /** 工具编码（唯一标识，供 Agent 调用引用）*/
    defCode: string;
    /** 适用智能体类型编码 */
    agentType: string;
    /** 工具配置（参数 Schema / 调用方式等 JSON 字符串）*/
    config: string;
    /** 描述 */
    description: string;
    /** 启用状态：0 禁用，1 启用 */
    status: number;
    /** 创建时间（ISO 字符串）*/
    createTime: string;
  }

  /** 工具定义分页查询参数 */
  export interface DefinitionPageQuery {
    /** 页码，从 1 开始 */
    pageNum?: number;
    /** 每页条数 */
    pageSize?: number;
    /** 名称模糊匹配（可选）*/
    defName?: string;
  }

  /** 工具定义创建/更新请求参数 */
  export interface DefinitionDTO {
    /** 工具名称 */
    defName?: string;
    /** 工具编码（唯一标识）*/
    defCode?: string;
    /** 适用智能体类型编码 */
    agentType?: string;
    /** 工具配置（JSON 字符串）*/
    config?: string;
    /** 描述 */
    description?: string;
    /** 启用状态：0 禁用，1 启用 */
    status?: number;
  }
}

/**
 * 分页查询 Agent 工具（Definition）列表。
 *
 * @param params - 分页与筛选条件（页码、页大小、名称模糊匹配）
 * @returns 分页结果，含 total 与当前页 items 等元信息
 */
export function getDefinitionPageApi(params: DefinitionApi.DefinitionPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DefinitionApi.DefinitionVO[];
  }>(`/api/v1/agent/definitions/page`, { params });
}

/**
 * 查询全部工具列表（不分页）。
 *
 * @returns 工具视图对象数组
 */
export function getDefinitionListApi() {
  return requestClient.get<DefinitionApi.DefinitionVO[]>(`/api/v1/agent/definitions/list`);
}

/**
 * 根据 ID 查询单个工具详情。
 *
 * @param id - 工具唯一 ID
 * @returns 工具视图对象
 */
export function getDefinitionByIdApi(id: string) {
  return requestClient.get<DefinitionApi.DefinitionVO>(`/api/v1/agent/definitions/${id}`);
}

/**
 * 创建工具定义。
 *
 * @param data - 创建参数（名称、编码、配置等）
 * @returns 新建记录的 ID
 */
export function createDefinitionApi(data: DefinitionApi.DefinitionDTO) {
  return requestClient.post<string>(`/api/v1/agent/definitions`, data);
}

/**
 * 更新工具定义（按 ID 全量更新）。
 *
 * @param data - 更新参数，需包含待更新记录的 ID
 * @returns 是否更新成功
 */
export function updateDefinitionApi(data: DefinitionApi.DefinitionDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/definitions`, data);
}

/**
 * 删除工具定义。
 *
 * @param id - 待删除的工具 ID
 * @returns 是否删除成功
 */
export function deleteDefinitionApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/definitions/${id}`);
}
