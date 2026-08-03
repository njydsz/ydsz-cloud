/**
 * 字典项 API 模块（前端）
 *
 * 封装字典项（{@code ydsz_dict_item}）CRUD 接口，对应后端 {@code /api/v1/dict/item/*} 端点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace DictitemApi {
  /** 字典项视图对象 */
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

  /** 字典项分页查询参数 */
  export interface DictitemPageQuery {
    pageNum?: number;
    pageSize?: number;
    typeCode?: string;
    itemCode?: string;
    status?: string;
  }

  /** 字典项创建/更新请求参数 */
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

/** 分页查询字典项 */
export function getDictitemPageApi(params: DictitemApi.DictitemPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DictitemApi.DictitemVO[];
  }>('/api/v1/dict/item/page', { params });
}

/** 查询全部字典项 */
export function getDictitemListApi() {
  return requestClient.get<DictitemApi.DictitemVO[]>('/api/v1/dict/item/list');
}

/** 根据 ID 查询字典项 */
export function getDictitemByIdApi(id: string) {
  return requestClient.get<DictitemApi.DictitemVO>(`/api/v1/dict/item/${id}`);
}

/** 创建字典项 */
export function createDictitemApi(data: DictitemApi.DictitemDTO) {
  return requestClient.post<string>('/api/v1/dict/item', data);
}

/** 更新字典项 */
export function updateDictitemApi(data: DictitemApi.DictitemDTO) {
  return requestClient.put<boolean>('/api/v1/dict/item', data);
}

/** 删除字典项 */
export function deleteDictitemApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/dict/item/${id}`);
}

/** 按类型编码查询启用的字典项列表 */
export function getDictItemListByTypeApi(typeCode: string) {
  return requestClient.get<DictitemApi.DictitemVO[]>(
    `/api/v1/dict/item/type/${typeCode}`,
  );
}
