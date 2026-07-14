/**
 * @file 标签管理 API
 * @description 提供文件标签的创建、删除、绑定、查询能力，对应后端 TagController（/nextwiki/tag）。
 * @module api/nextwiki/tag
 */
import { request } from '@/utils/request'
import type { TagVO, CreateTagDTO, BindTagDTO } from './types'

/**
 * 创建标签
 * @param data 标签创建参数
 * @returns 新建标签 ID
 */
export const createTag = (data: CreateTagDTO) =>
  request<string>({ url: '/nextwiki/tag/create', method: 'POST', data })

/**
 * 删除标签
 * @param id 标签 ID
 */
export const deleteTag = (id: string) =>
  request<void>({ url: `/nextwiki/tag/${id}`, method: 'DELETE' })

/**
 * 绑定标签到文件
 * @param data 标签绑定参数
 */
export const bindTag = (data: BindTagDTO) =>
  request<void>({ url: '/nextwiki/tag/bind', method: 'POST', data })

/**
 * 查询全部标签列表
 * @returns 标签列表
 */
export const listTags = () =>
  request<TagVO[]>({ url: '/nextwiki/tag/list', method: 'GET' })

/**
 * 查询文件已绑定的标签
 * @param fileId 文件节点 ID
 * @returns 标签列表
 */
export const getFileTags = (fileId: string) =>
  request<TagVO[]>({ url: `/nextwiki/tag/file/${fileId}`, method: 'GET' })
