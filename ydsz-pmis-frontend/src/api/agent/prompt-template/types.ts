/**
 * @file Prompt 模板类型定义
 * @module api/agent/prompt-template
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** Prompt 模板 DO */
export interface PromptTemplate {
  id: string
  code: string
  name: string
  content: string
  description?: string
  version: number
  active: boolean
  tenantId?: string
  createdBy?: string
  createdAt: string
  updatedAt: string
}

/** 创建模板 DTO */
export interface PromptTemplateCreateDTO {
  code: string
  name: string
  content: string
  description?: string
}

/** 查询模板 DTO */
export interface PromptTemplateQueryDTO {
  page?: number
  size?: number
  code?: string
  name?: string
  active?: boolean
}
