import { requestClient } from '#/api/request';

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
    typeCode?: string;
    typeName?: string;
    remark?: string;
    status?: number;
  }
}

/** 分页查询dictType列表 */
export function getDicttypePageApi(params: DicttypeApi.DicttypePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DicttypeApi.DicttypeVO[];
  }>(`/api/v1/dict/type/page`, { params });
}

/** 查询全部dictType列表 */
export function getDicttypeListApi() {
  return requestClient.get<DicttypeApi.DicttypeVO[]>(`/api/v1/dict/type/list`);
}

/** 根据 ID 查询dictType */
export function getDicttypeByIdApi(id: string) {
  return requestClient.get<DicttypeApi.DicttypeVO>(`/api/v1/dict/type/${id}`);
}

/** 创建dictType */
export function createDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return requestClient.post<string>(`/api/v1/dict/type`, data);
}

/** 更新dictType */
export function updateDicttypeApi(data: DicttypeApi.DicttypeDTO) {
  return requestClient.put<boolean>(`/api/v1/dict/type`, data);
}

/** 删除dictType */
export function deleteDicttypeApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/dict/type/${id}`);
}
