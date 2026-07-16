/**
 * @file 变更日志 API 封装
 * @description 对接后端 ChangelogController（/system/changelog），提供系统版本变更日志查询。
 * @module api/system/changelog
 */
import { request } from '@/utils/request'

/** 变更类型 */
export type ChangelogType = 'FEATURE' | 'IMPROVEMENT' | 'BUGFIX' | 'SECURITY'

/** 变更分类 */
export type ChangelogCategory = 'frontend' | 'backend' | 'infra' | 'security'

/** 变更日志条目 */
export interface ChangelogEntry {
  /** 版本号 */
  version: string
  /** 发布日期 */
  releaseDate: string
  /** 变更类型 */
  type: ChangelogType
  /** 变更标题 */
  title: string
  /** 变更描述 */
  description: string
  /** 变更分类 */
  category: ChangelogCategory
}

/**
 * 查询系统变更日志
 * @returns 变更日志列表（按版本倒序）
 */
export const getChangelog = () =>
  request<ChangelogEntry[]>({
    url: '/system/changelog',
    method: 'GET',
    silent: true,
  })
