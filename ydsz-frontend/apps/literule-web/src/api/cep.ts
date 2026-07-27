import { requestClient } from '#/api/request';

export namespace CepApi {
  export interface CepVO {
    id: string;
    cepName: string;
    cepPattern: string;
    windowSize: number;
    description: string;
    status: number;
    createTime: string;
  }

  export interface CepPageQuery {
    pageNum?: number;
    pageSize?: number;
    cepName?: string;
  }

  export interface CepDTO {
    cepName?: string;
    cepPattern?: string;
    windowSize?: number;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getCepPageApi(params: CepApi.CepPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: CepApi.CepVO[];
  }>(`/api/v1/literule/cep/page`, { params });
}

/** 查询全部列表 */
export function getCepListApi() {
  return requestClient.get<CepApi.CepVO[]>(`/api/v1/literule/cep/list`);
}

/** 根据 ID 查询 */
export function getCepByIdApi(id: string) {
  return requestClient.get<CepApi.CepVO>(`/api/v1/literule/cep/${id}`);
}

/** 创建 */
export function createCepApi(data: CepApi.CepDTO) {
  return requestClient.post<string>(`/api/v1/literule/cep`, data);
}

/** 更新 */
export function updateCepApi(data: CepApi.CepDTO) {
  return requestClient.put<boolean>(`/api/v1/literule/cep`, data);
}

/** 删除 */
export function deleteCepApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/literule/cep/${id}`);
}
