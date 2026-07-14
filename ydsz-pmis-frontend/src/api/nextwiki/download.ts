/**
 * @file 文件下载 API
 * @description 提供文件下载能力，对应后端 DownloadController（/nextwiki/download）。
 *              使用 Blob 接收二进制流并触发浏览器下载。
 * @module api/nextwiki/download
 */
import { request } from '@/utils/request'

/**
 * 下载文件（返回 Blob 二进制流）
 * @param id 文件节点 ID
 * @returns Blob 文件数据
 */
export const downloadFile = (id: string) =>
  request<Blob>({
    url: `/nextwiki/download/${id}`,
    method: 'POST',
    responseType: 'blob',
  })
