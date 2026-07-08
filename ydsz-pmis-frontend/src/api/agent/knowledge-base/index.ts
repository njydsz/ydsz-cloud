/**
 * @file 知识库管理 API
 * @description 对应后端 KnowledgeBaseController (/agent/knowledge-base)
 * @module api/agent/knowledge-base
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { KnowledgeBase, AgentDocument, RetrievedChunk } from './types'

/** 创建知识库 */
export const create = (kb: Partial<KnowledgeBase>) =>
  request<KnowledgeBase>({
    url: '/agent/knowledge-base',
    method: 'POST',
    data: kb,
  })

/** 查询知识库详情 */
export const getById = (id: string) =>
  request<KnowledgeBase>({
    url: `/agent/knowledge-base/${id}`,
    method: 'GET',
  })

/** 分页查询知识库 */
export const page = (pageNo: number, pageSize: number, tenantId?: string) =>
  request<PageResult<KnowledgeBase>>({
    url: '/agent/knowledge-base/page',
    method: 'GET',
    params: { page: pageNo, size: pageSize, tenantId },
  })

/** 上传文档到知识库 */
export const uploadDocument = (
  knowledgeBaseId: string,
  data: { name: string; sourceType?: string; content: string },
) =>
  request<AgentDocument>({
    url: `/agent/knowledge-base/${knowledgeBaseId}/documents`,
    method: 'POST',
    data,
  })

/** 查询知识库下的文档列表 */
export const listDocuments = (knowledgeBaseId: string) =>
  request<AgentDocument[]>({
    url: `/agent/knowledge-base/${knowledgeBaseId}/documents`,
    method: 'GET',
  })

/** 检索知识库 */
export const search = (knowledgeBaseId: string, query: string) =>
  request<RetrievedChunk[]>({
    url: `/agent/knowledge-base/${knowledgeBaseId}/search`,
    method: 'POST',
    data: { query },
  })
