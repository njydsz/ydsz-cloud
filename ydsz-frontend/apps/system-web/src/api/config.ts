/**
 * 系统配置 API 模块（前端）
 *
 * 封装系统参数（{@code ydsz_config}）CRUD 接口，对应后端 {@code /api/v1/config/*} 端点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace ConfigApi {
  export interface ConfigVO {
    id: string;
    configKey: string;
    configValue: string;
    configGroup: string;
    configName: string;
    valueType: string;
    isPublic: number;
    remark: string;
    createTime: string;
  }

  export interface ConfigPageQuery {
    pageNum?: number;
    pageSize?: number;
    configKey?: string;
    configGroup?: string;
  }

  export interface ConfigDTO {
    id?: string;
    configKey?: string;
    configValue?: string;
    configGroup?: string;
    configName?: string;
    valueType?: string;
    isPublic?: number;
    remark?: string;
  }
}

/** 分页查询系统配置列表 */
export function getConfigPageApi(params: ConfigApi.ConfigPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ConfigApi.ConfigVO[];
  }>('/api/v1/config/page', { params });
}

/** 查询全部系统配置列表 */
export function getConfigListApi() {
  return requestClient.get<ConfigApi.ConfigVO[]>('/api/v1/config/list');
}

/** 根据 ID 查询系统配置 */
export function getConfigByIdApi(id: string) {
  return requestClient.get<ConfigApi.ConfigVO>(`/api/v1/config/${id}`);
}

/** 创建系统配置 */
export function createConfigApi(data: ConfigApi.ConfigDTO) {
  return requestClient.post<string>('/api/v1/config', data);
}

/** 更新系统配置 */
export function updateConfigApi(data: ConfigApi.ConfigDTO) {
  return requestClient.put<boolean>('/api/v1/config', data);
}

/** 删除系统配置 */
export function deleteConfigApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/config/${id}`);
}
