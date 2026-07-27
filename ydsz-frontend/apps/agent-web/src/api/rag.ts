import { requestClient } from '#/api/request';

export namespace RagApi {
  export interface RagVO {
    id: string;
    knowledgeName: string;
    sourceType: string;
    sourcePath: string;
    chunkSize: number;
    chunkOverlap: number;
    status: number;
    createTime: string;
  }

  export interface RagPageQuery {
    pageNum?: number;
    pageSize?: number;
    knowledgeName?: string;
  }

  export interface RagDTO {
    knowledgeName?: string;
    sourceType?: string;
    sourcePath?: string;
    chunkSize?: number;
    chunkOverlap?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getRagPageApi(params: RagApi.RagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RagApi.RagVO[];
  }>(`/api/v1/agent/rag/page`, { params });
}

/** 查询全部列表 */
export function getRagListApi() {
  return requestClient.get<RagApi.RagVO[]>(`/api/v1/agent/rag/list`);
}

/** 根据 ID 查询 */
export function getRagByIdApi(id: string) {
  return requestClient.get<RagApi.RagVO>(`/api/v1/agent/rag/${id}`);
}

/** 创建 */
export function createRagApi(data: RagApi.RagDTO) {
  return requestClient.post<string>(`/api/v1/agent/rag`, data);
}

/** 更新 */
export function updateRagApi(data: RagApi.RagDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/rag`, data);
}

/** 删除 */
export function deleteRagApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/rag/${id}`);
}
