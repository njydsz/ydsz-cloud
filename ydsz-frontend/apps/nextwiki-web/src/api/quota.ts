import { requestClient } from '#/api/request';

export namespace QuotaApi {
  export interface QuotaVO {
    id: string;
    userId: string;
    totalQuota: number;
    usedQuota: number;
    status: number;
    createTime: string;
  }

  export interface QuotaPageQuery {
    pageNum?: number;
    pageSize?: number;
    userId?: string;
  }

  export interface QuotaDTO {
    userId?: string;
    totalQuota?: number;
  }
}

/** 分页查询 */
export function getQuotaPageApi(params: QuotaApi.QuotaPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: QuotaApi.QuotaVO[];
  }>(`/api/v1/nextwiki/quota/page`, { params });
}

/** 查询全部列表 */
export function getQuotaListApi() {
  return requestClient.get<QuotaApi.QuotaVO[]>(`/api/v1/nextwiki/quota/list`);
}

/** 根据 ID 查询 */
export function getQuotaByIdApi(id: string) {
  return requestClient.get<QuotaApi.QuotaVO>(`/api/v1/nextwiki/quota/${id}`);
}

/** 创建 */
export function createQuotaApi(data: QuotaApi.QuotaDTO) {
  return requestClient.post<string>(`/api/v1/nextwiki/quota`, data);
}

/** 更新 */
export function updateQuotaApi(data: QuotaApi.QuotaDTO) {
  return requestClient.put<boolean>(`/api/v1/nextwiki/quota`, data);
}

/** 删除 */
export function deleteQuotaApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/nextwiki/quota/${id}`);
}
