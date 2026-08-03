/**
 * 系统变量 API 模块（前端）
 * <p>封装系统变量（{@code ydsz_system_variable}）CRUD 接口，对应后端 {@code /api/v1/system/variable/*} 端点。
 * <p>系统变量是供业务代码读取的命名常量，支持加密存储。
 * <p>供「系统管理 → 变量管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace VariableApi {
  /** 系统变量视图对象 */
  export interface VariableVO {
    id: string;
    variableName: string;
    variableType: string;
    defaultValue: string;
    description: string;
    status: number;
    createTime: string;
  }

  /** 系统变量分页查询参数 */
  export interface VariablePageQuery {
    pageNum?: number;
    pageSize?: number;
    variableName?: string;
  }

  /** 系统变量创建/更新请求参数 */
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
  }>(`/api/v1/literule/variables/page`, { params });
}

/** 查询全部列表 */
export function getVariableListApi() {
  return requestClient.get<VariableApi.VariableVO[]>(`/api/v1/literule/variables/list`);
}

/** 根据 ID 查询 */
export function getVariableByIdApi(id: string) {
  return requestClient.get<VariableApi.VariableVO>(`/api/v1/literule/variables/${id}`);
}

/** 创建 */
export function createVariableApi(data: VariableApi.VariableDTO) {
  return requestClient.post<string>(`/api/v1/literule/variables`, data);
}

/** 更新 */
export function updateVariableApi(data: VariableApi.VariableDTO) {
  return requestClient.put<boolean>(`/api/v1/literule/variables`, data);
}

/** 删除 */
export function deleteVariableApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/literule/variables/${id}`);
}
