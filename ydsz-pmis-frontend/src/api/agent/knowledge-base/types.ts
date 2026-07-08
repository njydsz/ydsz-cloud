/**
 * @file 知识库类型定义
 * @module api/agent/knowledge-base
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 知识库 DO */
export interface KnowledgeBase {
  id: string
  name: string
  description?: string
  embeddingModel?: string
  chunkSize?: number
  chunkOverlap?: number
  documentCount?: number
  tenantId?: string
  createdAt: string
  updatedAt: string
}

/** 知识库文档 DO */
export interface AgentDocument {
  id: string
  knowledgeBaseId: string
  name: string
  sourceType?: string
  content?: string
  chunkCount?: number
  status?: string
  createdAt: string
  updatedAt: string
}

/** 检索结果片段 */
export interface RetrievedChunk {
  documentId?: string
  documentName?: string
  content: string
  score: number
  metadata?: Record<string, unknown>
}
