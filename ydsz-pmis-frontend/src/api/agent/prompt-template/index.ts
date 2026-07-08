/**
 * @file Prompt 模板管理 API
 * @description 对应后端 PromptTemplateController (/agent/prompt-template)
 * @module api/agent/prompt-template
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { PromptTemplate, PromptTemplateCreateDTO, PromptTemplateQueryDTO } from './types'

/** 创建模板 */
export const create = (dto: PromptTemplateCreateDTO) =>
  request<PromptTemplate>({
    url: '/agent/prompt-template',
    method: 'POST',
    data: dto,
  })

/** 激活模板 */
export const activate = (id: string) =>
  request<PromptTemplate>({
    url: `/agent/prompt-template/${id}/activate`,
    method: 'POST',
  })

/** 查询模板详情 */
export const getById = (id: string) =>
  request<PromptTemplate>({
    url: `/agent/prompt-template/${id}`,
    method: 'GET',
  })

/** 分页查询模板 */
export const page = (query: PromptTemplateQueryDTO) =>
  request<PageResult<PromptTemplate>>({
    url: '/agent/prompt-template',
    method: 'GET',
    params: query,
  })

/** 删除模板 */
export const remove = (id: string) =>
  request<void>({
    url: `/agent/prompt-template/${id}`,
    method: 'DELETE',
  })
