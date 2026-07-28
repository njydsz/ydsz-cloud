/**
 * 字典项 API 模块（前端）
 * <p>封装字典项（{@code ydsz_dict_item}）CRUD 接口，对应后端 {@code /api/v1/system/dictItem/*} 端点。
 * <p>字典项是字典类型的具体枚举值，支持状态/排序/扩展 JSON。
 * <p>供「系统管理 → 字典管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

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

/** 分页查询dictItem列表 */
export function getDictitemPageApi(params: DictitemApi.DictitemPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DictitemApi.DictitemVO[];
  }>(`/api/v1/dict/item/page`, { params });
}

/** 查询全部dictItem列表 */
export function getDictitemListApi() {
  return requestClient.get<DictitemApi.DictitemVO[]>(`/api/v1/dict/item/list`);
}

/** 根据 ID 查询dictItem */
export function getDictitemByIdApi(id: string) {
  return requestClient.get<DictitemApi.DictitemVO>(`/api/v1/dict/item/${id}`);
}

/** 创建dictItem */
export function createDictitemApi(data: DictitemApi.DictitemDTO) {
  return requestClient.post<string>(`/api/v1/dict/item`, data);
}

/** 更新dictItem */
export function updateDictitemApi(data: DictitemApi.DictitemDTO) {
  return requestClient.put<boolean>(`/api/v1/dict/item`, data);
}

/** 删除dictItem */
export function deleteDictitemApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/dict/item/${id}`);
}

/** 按类型编码查询启用的字典项列表 */
export function getDictItemListByTypeApi(typeCode: string) {
  return requestClient.get<DictItemApi.DictItemVO[]>(`/api/v1/dict/item/type/${typeCode}`);
}
