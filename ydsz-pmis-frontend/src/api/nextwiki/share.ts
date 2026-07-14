/**
 * @file 分享管理 API
 * @description 提供文件分享链接的创建、验证、查询、撤销能力，对应后端 ShareController（/nextwiki/share）。
 * @module api/nextwiki/share
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { ShareLinkVO, CreateShareDTO, VerifyShareDTO } from './types'

/**
 * 创建分享链接
 * @param data 分享创建参数
 * @returns 分享链接信息
 */
export const createShare = (data: CreateShareDTO) =>
  request<ShareLinkVO>({ url: '/nextwiki/share/create', method: 'POST', data })

/**
 * 验证分享链接（提取码/密码校验）
 * @param data 验证参数
 * @returns 验证通过后的文件信息
 */
export const verifyShare = (data: VerifyShareDTO) =>
  request<FileNodeVO>({ url: '/nextwiki/share/verify', method: 'POST', data })

/**
 * 分页查询我的分享列表
 * @param page 当前页码
 * @param size 每页条数
 * @returns 分享链接分页结果
 */
export const listShares = (page: number, size: number) =>
  request<PageResult<ShareLinkVO>>({
    url: '/nextwiki/share/list',
    method: 'GET',
    params: { page, size },
  })

/**
 * 撤销分享链接
 * @param id 分享 ID
 */
export const revokeShare = (id: string) =>
  request<void>({ url: `/nextwiki/share/${id}`, method: 'DELETE' })
