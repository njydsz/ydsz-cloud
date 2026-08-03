/**
 * 字典类型 API 模块（前端）
 *
 * 封装字典类型（{@code ydsz_dict_type}）CRUD 接口，对应后端 {@code /api/v1/dict/type/*} 端点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace DicttypeApi {
  /** 字典类型视图对象 */
  export interface DicttypeVO {
    id: string;
    typeCode: string;
    typeName: string;
    remark: string;
    status: number;
    createTime: string;
  }

  /** 字典类型分页查询参数 */
  export interface DicttypePageQuery {
    pageNum?: number;
    pageSize?: number;
    typeName?: string;
    typeCode?: string;
  }

  /** 字典类型创建/更新请求参数 */
  export interface DicttypeDTO {
    id?: string;
    typeCode?: string;
    typeName?: string;
    remark?: string;
    status?: number;
  }
}

/** 分页查询字典类型 */
export function getDicttypePageApi(params: DicttypeApi.DicttypePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DicttypeApi.DicttypeVO[];
  }>('/api/v1/dict/type/page', { params });
}

/** 查询全部字典类型（下拉框数据源） */
export function getDicttypeListApi() {
  return requestClient.get<DicttypeApi.DicttypeVO[]>('/api/v1/dict/type/list');
}

/** 根据 ID 查询字典类型 */
export function getDicttypeByIdApi(id: string) {
  return requestClient.get<DicttypeApi.DicttypeVO>(`/api/v1/dict/type/${id}`);
}

/** 创建字典类型 */
export function createDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return requestClient.post<string>('/api/v1/dict/type', data);
}

/** 更新字典类型 */
export function updateDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return requestClient.put<boolean>('/api/v1/dict/type', data);
}

/** 删除字典类型 */
export function deleteDicttypeApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/dict/type/${id}`);
}
