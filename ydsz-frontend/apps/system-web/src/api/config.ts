/**
 * 系统配置 API 模块（前端）
 *
 * 封装系统参数（{@code ydsz_config}）CRUD 接口，对应后端 {@code /api/v1/config/*} 端点。
 * 使用 @ydsz/shared-api 的 createCrudApi 工厂消除重复 CRUD 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';
import { createCrudApi } from '@ydsz/shared-api';

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

/** 系统配置 CRUD API（由 createCrudApi 工厂创建） */
export const configApi = createCrudApi<
  ConfigApi.ConfigVO,
  ConfigApi.ConfigPageQuery,
  ConfigApi.ConfigDTO
>(requestClient, '/api/v1/config');

/**
 * 分页查询 config 列表
 * @deprecated 使用 configApi.page() 替代
 */
export function getConfigPageApi(params: ConfigApi.ConfigPageQuery) {
  return configApi.page(params as any);
}

/**
 * 查询全部 config 列表
 * @deprecated 使用 configApi.list() 替代
 */
export function getConfigListApi() {
  return configApi.list();
}

/**
 * 根据 ID 查询 config
 * @deprecated 使用 configApi.getById() 替代
 */
export function getConfigByIdApi(id: string) {
  return configApi.getById(id);
}

/**
 * 创建 config
 * @deprecated 使用 configApi.create() 替代
 */
export function createConfigApi(data: ConfigApi.ConfigDTO) {
  return configApi.create(data);
}

/**
 * 更新 config
 * @deprecated 使用 configApi.update() 替代
 */
export function updateConfigApi(data: ConfigApi.ConfigDTO) {
  return configApi.update(data.id ?? '', data);
}

/**
 * 删除 config
 * @deprecated 使用 configApi.remove() 替代
 */
export function deleteConfigApi(id: string) {
  return configApi.remove(id);
}
