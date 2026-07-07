/**
 * @file 全局搜索 API
 * @description 项目全文检索（基于 PostgreSQL tsvector，P2-19 替代 ES），
 *              对应后端 SearchController
 *              /execution/search/projects（ 前缀由 baseURL 注入）
 * @module api/search
 */
import { request } from '@/utils/request'
import type { PageData } from '@/types/api'

/** 项目搜索结果（PG tsvector 检索，对应后端 ProjectSearchVO） */
export interface ProjectSearchDoc {
  /** 立项 ID */
  id: number
  /** 项目编号 */
  projectCode: string
  /** 项目名称 */
  projectName: string
  /** 客户名称 */
  customerName: string
  /** 合同名称（来自关联合同表，无合同时为空） */
  contractName: string
  /** 项目类型 */
  projectType: string
  /** 立项阶段 */
  stage: string
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
 * @returns 分页项目搜索结果；PG 检索异常时降级返回空页
 */
export const searchProjects = (keyword: string, page = 0, size = 10) =>
  request<PageData<ProjectSearchDoc>>({
    url: '/execution/search/projects',
    method: 'GET',
    params: { keyword, page, size },
    silent: true,
  })
