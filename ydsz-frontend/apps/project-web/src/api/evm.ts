import { requestClient } from '#/api/request';

export namespace EvmApi {
  export interface EvmVO {
    id: string;
    projectId: string;
    measureDate: string;
    pv: number;
    ev: number;
    ac: number;
    sv: number;
    cv: number;
    spi: number;
    cpi: number;
    createTime: string;
  }

  export interface EvmPageQuery {
    pageNum?: number;
    pageSize?: number;
    projectId?: string;
  }

  export interface EvmDTO {
    projectId?: string;
    measureDate?: string;
    pv?: number;
    ev?: number;
    ac?: number;
  }
}

/** 分页查询 */
export function getEvmPageApi(params: EvmApi.EvmPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: EvmApi.EvmVO[];
  }>(`/api/v1/project/evm/measure/page`, { params });
}

/** 查询全部列表 */
export function getEvmListApi() {
  return requestClient.get<EvmApi.EvmVO[]>(`/api/v1/project/evm/measure/list`);
}

/** 根据 ID 查询 */
export function getEvmByIdApi(id: string) {
  return requestClient.get<EvmApi.EvmVO>(`/api/v1/project/evm/measure/${id}`);
}

/** 创建 */
export function createEvmApi(data: EvmApi.EvmDTO) {
  return requestClient.post<string>(`/api/v1/project/evm/measure`, data);
}

/** 更新 */
export function updateEvmApi(data: EvmApi.EvmDTO) {
  return requestClient.put<boolean>(`/api/v1/project/evm/measure`, data);
}

/** 删除 */
export function deleteEvmApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/evm/measure/${id}`);
}
