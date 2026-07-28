/**
 * Agent RAG 检索增强 API 模块（前端）
 * <p>封装 RAG（Retrieval-Augmented Generation）向量检索接口，对应后端 {@code /api/v1/agent/rag/*} 端点。
 * <p>支持文档切片、向量化存储、相似度检索、Top-K 召回。
 * <p>供「Agent 知识库」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
