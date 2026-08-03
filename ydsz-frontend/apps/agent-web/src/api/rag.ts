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
  /** RAG 知识库视图对象 */
  export interface RagVO {
    /** 知识库唯一 ID */
    id: string;
    /** 知识库名称 */
    knowledgeName: string;
    /** 来源类型（如 file / web / database 等）*/
    sourceType: string;
    /** 来源路径或地址 */
    sourcePath: string;
    /** 文档切片大小（按字符/ token 数）*/
    chunkSize: number;
    /** 切片之间的重叠字符数，缓解上下文割裂 */
    chunkOverlap: number;
    /** 启用状态：0 禁用，1 启用 */
    status: number;
    /** 创建时间（ISO 字符串）*/
    createTime: string;
  }

  /** RAG 知识库分页查询参数 */
  export interface RagPageQuery {
    /** 页码，从 1 开始 */
    pageNum?: number;
    /** 每页条数 */
    pageSize?: number;
    /** 名称模糊匹配（可选）*/
    knowledgeName?: string;
  }

  /** RAG 知识库创建/更新请求参数 */
  export interface RagDTO {
    /** 知识库名称 */
    knowledgeName?: string;
    /** 来源类型 */
    sourceType?: string;
    /** 来源路径或地址 */
    sourcePath?: string;
    /** 文档切片大小 */
    chunkSize?: number;
    /** 切片重叠字符数 */
    chunkOverlap?: number;
    /** 启用状态：0 禁用，1 启用 */
    status?: number;
  }
}

/**
 * 分页查询 RAG 知识库列表。
 *
 * @param params - 分页与筛选条件（页码、页大小、名称模糊匹配）
 * @returns 分页结果，含 total 与当前页 items 等元信息
 */
export function getRagPageApi(params: RagApi.RagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RagApi.RagVO[];
  }>(`/api/v1/agent/rag/page`, { params });
}

/**
 * 查询全部知识库列表（不分页）。
 *
 * @returns 知识库视图对象数组
 */
export function getRagListApi() {
  return requestClient.get<RagApi.RagVO[]>(`/api/v1/agent/rag/list`);
}

/**
 * 根据 ID 查询单个知识库详情。
 *
 * @param id - 知识库唯一 ID
 * @returns 知识库视图对象
 */
export function getRagByIdApi(id: string) {
  return requestClient.get<RagApi.RagVO>(`/api/v1/agent/rag/${id}`);
}

/**
 * 创建知识库（触发文档切片与向量化）。
 *
 * @param data - 创建参数（名称、来源、切片配置等）
 * @returns 新建记录的 ID
 */
export function createRagApi(data: RagApi.RagDTO) {
  return requestClient.post<string>(`/api/v1/agent/rag`, data);
}

/**
 * 更新知识库（按 ID 全量更新）。
 *
 * @param data - 更新参数，需包含待更新记录的 ID
 * @returns 是否更新成功
 */
export function updateRagApi(data: RagApi.RagDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/rag`, data);
}

/**
 * 删除知识库。
 *
 * @param id - 待删除的知识库 ID
 * @returns 是否删除成功
 */
export function deleteRagApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/rag/${id}`);
}
