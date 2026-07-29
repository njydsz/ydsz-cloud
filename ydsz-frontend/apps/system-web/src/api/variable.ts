/**
 * 系统变量 API 模块（前端）
 *
 * 封装系统变量（{@code ydsz_system_variable}）CRUD 接口，对应后端 {@code /api/v1/variable/*} 端点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace VariableApi {
  export interface VariableVO {
    id: string;
    variableKey: string;
    variableValue: string;
    variableType: string;
    remark: string;
    status: number;
    createTime: string;
  }

  export interface VariablePageQuery {
    pageNum?: number;
    pageSize?: number;
    variableKey?: string;
    status?: string;
  }

  export interface VariableDTO {
    id?: string;
    variableKey?: string;
    variableValue?: string;
    variableType?: string;
    remark?: string;
    status?: number;
  }
}

/** 分页查询系统变量列表 */
export function getVariablePageApi(params: VariableApi.VariablePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: VariableApi.VariableVO[];
  }>('/api/v1/variable/page', { params });
}

/** 查询全部系统变量列表 */
export function getVariableListApi() {
  return requestClient.get<VariableApi.VariableVO[]>('/api/v1/variable/list');
}

/** 根据 ID 查询系统变量 */
export function getVariableByIdApi(id: string) {
  return requestClient.get<VariableApi.VariableVO>(`/api/v1/variable/${id}`);
}

/** 创建系统变量 */
export function createVariableApi(data: VariableApi.VariableDTO) {
  return requestClient.post<string>('/api/v1/variable', data);
}

/** 更新系统变量 */
export function updateVariableApi(data: VariableApi.VariableDTO) {
  return requestClient.put<boolean>('/api/v1/variable', data);
}

/** 删除系统变量 */
export function deleteVariableApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/variable/${id}`);
}
