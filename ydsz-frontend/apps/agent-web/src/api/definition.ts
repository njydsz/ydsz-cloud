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
  }>(`/api/v1/agent/definitions/page`, { params });
}

/** 查询全部列表 */
export function getDefinitionListApi() {
  return requestClient.get<DefinitionApi.DefinitionVO[]>(`/api/v1/agent/definitions/list`);
}

/** 根据 ID 查询 */
export function getDefinitionByIdApi(id: string) {
  return requestClient.get<DefinitionApi.DefinitionVO>(`/api/v1/agent/definitions/${id}`);
}

/** 创建 */
export function createDefinitionApi(data: DefinitionApi.DefinitionDTO) {
  return requestClient.post<string>(`/api/v1/agent/definitions`, data);
}

/** 更新 */
export function updateDefinitionApi(data: DefinitionApi.DefinitionDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/definitions`, data);
}

/** 删除 */
export function deleteDefinitionApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/definitions/${id}`);
}
