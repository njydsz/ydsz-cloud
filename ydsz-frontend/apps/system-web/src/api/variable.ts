/**
 * 系统变量 API 模块（前端）
 *
 * 封装系统变量（{@code ydsz_system_variable}）CRUD 接口，对应后端 {@code /api/v1/variable/*} 端点。
 * 使用 @ydsz/shared-api 的 createCrudApi 工厂消除重复 CRUD 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';
import { createCrudApi } from '@ydsz/shared-api';

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

/** 系统变量 CRUD API（由 createCrudApi 工厂创建） */
export const variableApi = createCrudApi<
  VariableApi.VariableVO,
  VariableApi.VariablePageQuery,
  VariableApi.VariableDTO
>(requestClient, '/api/v1/variable');

/** @deprecated 使用 variableApi.page() 替代 */
export function getVariablePageApi(params: VariableApi.VariablePageQuery) {
  return variableApi.page(params as any);
}

/** @deprecated 使用 variableApi.list() 替代 */
export function getVariableListApi() {
  return variableApi.list();
}

/** @deprecated 使用 variableApi.getById() 替代 */
export function getVariableByIdApi(id: string) {
  return variableApi.getById(id);
}

/** @deprecated 使用 variableApi.create() 替代 */
export function createVariableApi(data: VariableApi.VariableDTO) {
  return variableApi.create(data);
}

/** @deprecated 使用 variableApi.update() 替代 */
export function updateVariableApi(data: VariableApi.VariableDTO) {
  return variableApi.update(data.id ?? '', data);
}

/** @deprecated 使用 variableApi.remove() 替代 */
export function deleteVariableApi(id: string) {
  return variableApi.remove(id);
}
