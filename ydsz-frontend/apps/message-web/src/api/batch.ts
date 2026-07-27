import { requestClient } from '#/api/request';

export namespace BatchApi {
  export interface BatchVO {
    id: string;
    batchName: string;
    channel: string;
    totalcount: number;
    successCount: number;
    failCount: number;
    status: string;
    createTime: string;
  }

  export interface BatchPageQuery {
    pageNum?: number;
    pageSize?: number;
    batchName?: string;
  }

  export interface BatchDTO {
    batchName?: string;
    channel?: string;
  }
}

/** 分页查询 */
export function getBatchPageApi(params: BatchApi.BatchPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: BatchApi.BatchVO[];
  }>(`/batch/page`, { params });
}

/** 查询全部列表 */
export function getBatchListApi() {
  return requestClient.get<BatchApi.BatchVO[]>(`/batch/list`);
}

/** 根据 ID 查询 */
export function getBatchByIdApi(id: string) {
  return requestClient.get<BatchApi.BatchVO>(`/batch/${id}`);
}

/** 创建 */
export function createBatchApi(data: BatchApi.BatchDTO) {
  return requestClient.post<string>(`/api/v1/message/batch`, data);
}

/** 更新 */
export function updateBatchApi(data: BatchApi.BatchDTO) {
  return requestClient.put<boolean>(`/api/v1/message/batch`, data);
}

/** 删除 */
export function deleteBatchApi(id: string) {
  return requestClient.delete<boolean>(`/batch/${id}`);
}
