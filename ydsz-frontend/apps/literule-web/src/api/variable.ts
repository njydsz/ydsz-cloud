import { requestClient } from '#/api/request';

export namespace VariableApi {
  export interface VariableVO {
    id: string;
    variableName: string;
    variableType: string;
    defaultValue: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface VariablePageQuery {
    pageNum?: number;
    pageSize?: number;
    variableName?: string;
  }

  export interface VariableDTO {
    variableName?: string;
    variableType?: string;
    defaultValue?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getVariablePageApi(params: VariableApi.VariablePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: VariableApi.VariableVO[];
  }>(`/ruleEngine/variables/page`, { params });
}

/** 查询全部列表 */
export function getVariableListApi() {
  return requestClient.get<VariableApi.VariableVO[]>(`/ruleEngine/variables/list`);
}

/** 根据 ID 查询 */
export function getVariableByIdApi(id: string) {
  return requestClient.get<VariableApi.VariableVO>(`/ruleEngine/variables/${id}`);
}

/** 创建 */
export function createVariableApi(data: VariableApi.VariableDTO) {
  return requestClient.post<string>(`/ruleEngine/variables`, data);
}

/** 更新 */
export function updateVariableApi(data: VariableApi.VariableDTO) {
  return requestClient.put<boolean>(`/ruleEngine/variables`, data);
}

/** 删除 */
export function deleteVariableApi(id: string) {
  return requestClient.delete<boolean>(`/ruleEngine/variables/${id}`);
}
