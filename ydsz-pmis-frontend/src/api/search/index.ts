/**
 * @file 全局搜索 API
 * @description 项目全文检索（基于 Elasticsearch），对应后端 SearchController
 *              /api/v1/execution/search/projects（/api/v1 前缀由 baseURL 注入）
 * @module api/search
 */
import { request } from '@/utils/request'
import type { PageData } from '@/types/api'

/** 项目搜索文档（ES 索引结构，对应后端 ProjectSearchDoc） */
export interface ProjectSearchDoc {
  /** 文档 ID（与立项 ID 字符串一致） */
  id: string
  /** 立项 ID */
  initiationId: number
  /** 项目名称 */
  projectName: string
  /** 客户名称 */
  customerName: string
  /** 合同名称 */
  contractName: string
  /** 项目类型 */
  projectType: string
  /** 项目状态 */
  status: string
  /** 项目经理姓名 */
  pmName: string
  /** 创建时间 */
  createdAt: string
  /** 更新时间 */
  updatedAt: string
}

/**
 * 全文检索项目
 *
 * @param keyword 关键词
 * @param page    页码（从 0 开始，默认 0）
 * @param size    每页条数（默认 10）
 * @returns 分页项目搜索结果；ES 不可用时返回空页
 */
export const searchProjects = (keyword: string, page = 0, size = 10) =>
  request<PageData<ProjectSearchDoc>>({
    url: '/execution/search/projects',
    method: 'GET',
    params: { keyword, page, size },
    silent: true,
  })
