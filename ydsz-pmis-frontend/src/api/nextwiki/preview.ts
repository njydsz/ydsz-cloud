/**
 * @file 文件预览 API
 * @description 提供文件预览的生成与获取能力，对应后端 PreviewController（/nextwiki/preview）。
 * @module api/nextwiki/preview
 */
import { request } from '@/utils/request'
import type { FilePreviewVO } from './types'

/**
 * 生成文件预览（异步触发后端转换）
 * @param id 文件节点 ID
 */
export const generatePreview = (id: string) =>
  request<void>({ url: `/nextwiki/preview/${id}/generate`, method: 'POST' })

/**
 * 获取文件预览信息
 * @param id 文件节点 ID
 * @returns 预览信息（含预览 URL / 缩略图 / 文本内容等）
 */
export const getPreview = (id: string) =>
  request<FilePreviewVO>({ url: `/nextwiki/preview/${id}`, method: 'GET' })
