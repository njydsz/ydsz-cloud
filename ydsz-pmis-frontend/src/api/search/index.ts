/**
 * @file 全局搜索 API
 * @description 全文检索（基于 PostgreSQL tsvector），
 *              对应后端 SearchController
 *              /search/* （baseURL 由 VITE_API_BASE_URL 注入）
 *
 * P0-4: 扩展实体覆盖 — 项目 / 合同 / 审批任务 / 工单 / 人员 / 知识库 等
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

/** 搜索实体类型 */
export type SearchEntityType =
  | 'project'
  | 'contract'
  | 'approval'
  | 'ticket'
  | 'employee'
  | 'knowledge'

/** 统一搜索结果项（跨实体通用结构） */
export interface UniversalSearchDoc {
  /** 实体类型 */
  type: SearchEntityType
  /** 实体 ID */
  id: number | string
  /** 主标题（项目名 / 合同名 / 流程标题 / 工单标题 / 员工姓名 / 文档标题） */
  title: string
  /** 副标题（客户名 / 合同编号 / 流程编号 / 工单编号 / 部门 / 标签） */
  subtitle: string
  /** 状态 */
  status?: string
  /** 跳转路径（前端路由，已组装好查询参数） */
  path: string
}

/**
 * 全文检索项目
 *
 * @param keyword 关键词
 * @param page    页码（从 1 开始，默认 1，与后端 PageQuery 约定一致）
 * @param size    每页条数（默认 10）
 * @returns 分页项目搜索结果；PG 检索异常时降级返回空页
 */
export const searchProjects = (keyword: string, page = 1, size = 10) =>
  request<PageData<ProjectSearchDoc>>({
    url: '/api/project/search/projects',
    method: 'GET',
    params: { keyword, page, size },
    silent: true,
  })

/**
 * 统一搜索（跨实体）
 *
 * P0-4: 一次请求搜索项目 / 合同 / 审批 / 工单 / 人员 / 知识库，
 * 后端 SearchController 聚合返回分类结果。
 * 如后端尚未实现此端点，前端降级为仅调用 searchProjects。
 *
 * @param keyword 关键词
 * @param size    每类实体最大返回条数（默认 5）
 * @returns 分类搜索结果
 */
export const searchAll = (keyword: string, size = 5) =>
  request<UniversalSearchDoc[]>({
    url: '/api/project/search/all',
    method: 'GET',
    params: { keyword, size },
    silent: true,
  })
