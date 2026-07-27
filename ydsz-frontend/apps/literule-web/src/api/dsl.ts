import { requestClient } from '#/api/request';

export namespace DslApi {
  export interface DslVO {
    id: string;
    dslName: string;
    dslContent: string;
    dslType: string;
    status: number;
    createTime: string;
  }

  export interface DslPageQuery {
    pageNum?: number;
    pageSize?: number;
    dslName?: string;
  }

  export interface DslDTO {
    dslName?: string;
    dslContent?: string;
    dslType?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getDslPageApi(params: DslApi.DslPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DslApi.DslVO[];
  }>(`/ruleEngine/dsl/page`, { params });
}

/** 查询全部列表 */
export function getDslListApi() {
  return requestClient.get<DslApi.DslVO[]>(`/ruleEngine/dsl/list`);
}

/** 根据 ID 查询 */
export function getDslByIdApi(id: string) {
  return requestClient.get<DslApi.DslVO>(`/ruleEngine/dsl/${id}`);
}

/** 创建 */
export function createDslApi(data: DslApi.DslDTO) {
  return requestClient.post<string>(`/ruleEngine/dsl`, data);
}

/** 更新 */
export function updateDslApi(data: DslApi.DslDTO) {
  return requestClient.put<boolean>(`/ruleEngine/dsl`, data);
}

/** 删除 */
export function deleteDslApi(id: string) {
  return requestClient.delete<boolean>(`/ruleEngine/dsl/${id}`);
}
