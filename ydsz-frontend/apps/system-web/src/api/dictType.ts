/**
 * 字典类型 API 模块（前端）
 *
 * 封装字典类型（{@code ydsz_dict_type}）CRUD 接口，对应后端 {@code /api/v1/dict/type/*} 端点。
 * 使用 @ydsz/shared-api 的 createCrudApi 工厂消除重复 CRUD 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';
import { createCrudApi } from '@ydsz/shared-api';

export namespace DicttypeApi {
  export interface DicttypeVO {
    id: string;
    typeCode: string;
    typeName: string;
    remark: string;
    status: number;
    createTime: string;
  }

  export interface DicttypePageQuery {
    pageNum?: number;
    pageSize?: number;
    typeName?: string;
    typeCode?: string;
  }

  export interface DicttypeDTO {
    id?: string;
    typeCode?: string;
    typeName?: string;
    remark?: string;
    status?: number;
  }
}

/** 字典类型 CRUD API（由 createCrudApi 工厂创建） */
export const dicttypeApi = createCrudApi<
  DicttypeApi.DicttypeVO,
  DicttypeApi.DicttypePageQuery,
  DicttypeApi.DicttypeDTO
>(requestClient, '/api/v1/dict/type');

/**
 * 分页查询字典类型
 * @deprecated 使用 dicttypeApi.page() 替代
 */
export function getDicttypePageApi(params: DicttypeApi.DicttypePageQuery) {
  return dicttypeApi.page(params as any);
}

/**
 * 查询全部字典类型（下拉框数据源）
 * @deprecated 使用 dicttypeApi.list() 替代
 */
export function getDicttypeListApi() {
  return dicttypeApi.list();
}

/**
 * 根据 ID 查询字典类型
 * @deprecated 使用 dicttypeApi.getById() 替代
 */
export function getDicttypeByIdApi(id: string) {
  return dicttypeApi.getById(id);
}

/**
 * 创建字典类型
 * @deprecated 使用 dicttypeApi.create() 替代
 */
export function createDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return dicttypeApi.create(data);
}

/**
 * 更新字典类型
 * @deprecated 使用 dicttypeApi.update() 替代
 */
export function updateDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return dicttypeApi.update(data.id ?? '', data);
}

/**
 * 删除字典类型
 * @deprecated 使用 dicttypeApi.remove() 替代
 */
export function deleteDicttypeApi(id: string) {
  return dicttypeApi.remove(id);
}
