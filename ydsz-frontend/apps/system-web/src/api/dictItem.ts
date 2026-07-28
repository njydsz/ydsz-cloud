/**
 * 字典项 API 模块（前端）
 *
 * 封装字典项（{@code ydsz_dict_item}）CRUD 接口，对应后端 {@code /api/v1/dict/item/*} 端点。
 * 使用 @ydsz/shared-api 的 createCrudApi 工厂消除重复 CRUD 代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';
import { createCrudApi } from '@ydsz/shared-api';

export namespace DictitemApi {
  export interface DictitemVO {
    id: string;
    typeCode: string;
    itemCode: string;
    itemText: string;
    itemValue: string;
    sort: number;
    status: number;
    parentId: string;
    remark: string;
    createTime: string;
  }

  export interface DictitemPageQuery {
    pageNum?: number;
    pageSize?: number;
    typeCode?: string;
    itemCode?: string;
    status?: string;
  }

  export interface DictitemDTO {
    id?: string;
    typeCode?: string;
    itemCode?: string;
    itemText?: string;
    itemValue?: string;
    sort?: number;
    status?: number;
    parentId?: string;
    remark?: string;
  }
}

/** 字典项 CRUD API（由 createCrudApi 工厂创建） */
export const dictitemApi = createCrudApi<
  DictitemApi.DictitemVO,
  DictitemApi.DictitemPageQuery,
  DictitemApi.DictitemDTO
>(requestClient, '/api/v1/dict/item');

/**
 * 分页查询字典项
 * @deprecated 使用 dictitemApi.page() 替代
 */
export function getDictitemPageApi(params: DictitemApi.DictitemPageQuery) {
  return dictitemApi.page(params as any);
}

/**
 * 查询全部字典项
 * @deprecated 使用 dictitemApi.list() 替代
 */
export function getDictitemListApi() {
  return dictitemApi.list();
}

/**
 * 根据 ID 查询字典项
 * @deprecated 使用 dictitemApi.getById() 替代
 */
export function getDictitemByIdApi(id: string) {
  return dictitemApi.getById(id);
}

/**
 * 创建字典项
 * @deprecated 使用 dictitemApi.create() 替代
 */
export function createDictitemApi(data: DictitemApi.DictitemDTO) {
  return dictitemApi.create(data);
}

/**
 * 更新字典项
 * @deprecated 使用 dictitemApi.update() 替代
 */
export function updateDictitemApi(data: DictitemApi.DictitemDTO) {
  return dictitemApi.update(data.id ?? '', data);
}

/**
 * 删除字典项
 * @deprecated 使用 dictitemApi.remove() 替代
 */
export function deleteDictitemApi(id: string) {
  return dictitemApi.remove(id);
}

/** 按类型编码查询启用的字典项列表 */
export function getDictItemListByTypeApi(typeCode: string) {
  return requestClient.get<DictitemApi.DictitemVO[]>(`/api/v1/dict/item/type/${typeCode}`);
}
