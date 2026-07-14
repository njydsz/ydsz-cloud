/**
 * @file 文件管理 API
 * @description 提供网盘文件/文件夹的上传、创建、查询、移动、重命名、删除、复制、
 *              版本管理、收藏等能力，对应后端 FileController（/nextwiki/files）。
 * @module api/nextwiki/file
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type {
  FileNodeVO,
  FileVersionVO,
  CreateFolderDTO,
  MoveFileDTO,
  RenameDTO,
  BatchDeleteDTO,
  BatchMoveDTO,
  StarDTO,
  FileListQuery,
} from './types'

/**
 * 查询文件列表（支持按父目录、关键字、类型、收藏筛选）
 * @param params 查询条件
 * @returns 文件节点分页结果
 */
export const listFiles = (params: FileListQuery & { page?: number; size?: number }) =>
  request<PageResult<FileNodeVO>>({
    url: '/nextwiki/files/list',
    method: 'GET',
    params,
  })

/**
 * 上传文件（multipart/form-data）
 * @param formData 包含 file 和 parentId 的表单数据
 * @returns 新建文件节点 ID
 */
export const uploadFile = (formData: FormData) =>
  request<string>({
    url: '/nextwiki/files/upload',
    method: 'POST',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })

/**
 * 新建文件夹
 * @param data 文件夹创建参数
 * @returns 新建文件夹 ID
 */
export const createFolder = (data: CreateFolderDTO) =>
  request<string>({ url: '/nextwiki/files/folders', method: 'POST', data })

/**
 * 移动文件/文件夹到目标目录
 * @param id 文件节点 ID
 * @param data 移动目标参数
 */
export const moveFile = (id: string, data: MoveFileDTO) =>
  request<void>({ url: `/nextwiki/files/${id}/move`, method: 'PUT', data })

/**
 * 重命名文件/文件夹
 * @param id 文件节点 ID
 * @param data 新名称参数
 */
export const renameFile = (id: string, data: RenameDTO) =>
  request<void>({ url: `/nextwiki/files/${id}/rename`, method: 'PUT', data })

/**
 * 删除文件/文件夹（移入回收站）
 * @param id 文件节点 ID
 */
export const deleteFile = (id: string) =>
  request<void>({ url: `/nextwiki/files/${id}`, method: 'DELETE' })

/**
 * 批量删除文件
 * @param data 批量删除参数（ID 列表）
 */
export const batchDeleteFiles = (data: BatchDeleteDTO) =>
  request<void>({ url: '/nextwiki/files/batch/delete', method: 'POST', data })

/**
 * 批量移动文件
 * @param data 批量移动参数
 */
export const batchMoveFiles = (data: BatchMoveDTO) =>
  request<void>({ url: '/nextwiki/files/batch/move', method: 'POST', data })

/**
 * 复制文件到目标目录
 * @param id 源文件节点 ID
 * @param data 目标目录参数
 */
export const copyFile = (id: string, data: MoveFileDTO) =>
  request<string>({ url: `/nextwiki/files/${id}/copy`, method: 'POST', data })

/**
 * 查询文件历史版本列表
 * @param id 文件节点 ID
 * @returns 版本列表
 */
export const listFileVersions = (id: string) =>
  request<FileVersionVO[]>({ url: `/nextwiki/files/${id}/versions`, method: 'GET' })

/**
 * 回滚到指定版本
 * @param id 文件节点 ID
 * @param version 版本号
 */
export const rollbackVersion = (id: string, version: number) =>
  request<void>({ url: `/nextwiki/files/${id}/versions/${version}/rollback`, method: 'POST' })

/**
 * 切换文件收藏状态
 * @param id 文件节点 ID
 * @param data 收藏状态参数
 */
export const toggleStar = (id: string, data: StarDTO) =>
  request<void>({ url: `/nextwiki/files/${id}/star`, method: 'PUT', data })
