/**
 * @file 分布式任务引擎(cronjob) API 接口封装
 * @description 提供任务管理、执行日志、子任务、GLUE 在线编码、版本管理、统计趋势、SLA 管理等能力，
 *              对应后端 CronjobController（/cronjob）。
 *
 *   - 所有 ID 类型为 string（后端 VARCHAR(20) 雪花算法字符串）
 *   - 分页接口返回 PageData<T>
 *   - GET 请求用 params，POST/PUT 用 data
 *   - 查询类接口加 silent: true，不触发全局 loading
 * @module api/cronjob
 */
import { request } from '@/utils/request'
import type { PageData } from '@/types/api'
import type {
  JobVO,
  JobSaveDTO,
  JobPageQuery,
  JobLogVO,
  JobLogPageQuery,
  JobLogContentVO,
  JobLogContentPageQuery,
  JobTaskVO,
  JobTaskPageQuery,
  JobDailyStatsVO,
  JobSummaryStatsVO,
  JobDailyStatsQuery,
  JobSlaVO,
  JobSlaSaveDTO,
  JobSlaPageQuery,
  JobHistoryVO,
  GlueCodeVO,
  GlueCodeSaveDTO,
  JobBatchRequest,
} from './types'

/** cronjob 接口路径前缀（baseURL 由 VITE_API_BASE_URL 注入） */
const BASE = '/cronjob'

// ==================== 任务管理 ====================

/**
 * 分页查询任务
 * @param params 分页与筛选条件
 * @returns 任务分页数据
 */
export const getJobPage = (params: JobPageQuery) =>
  request<PageData<JobVO>>({ url: `${BASE}/page`, method: 'GET', params, silent: true })

/**
 * 创建任务
 * @param data 任务参数
 */
export const createJob = (data: JobSaveDTO) =>
  request<void>({ url: BASE, method: 'POST', data })

/**
 * 更新任务
 * @param data 任务参数（含 id）
 */
export const updateJob = (data: JobSaveDTO) =>
  request<void>({ url: BASE, method: 'PUT', data })

/**
 * 删除任务
 * @param id 任务 ID
 */
export const deleteJob = (id: string) =>
  request<void>({ url: `${BASE}/${id}`, method: 'DELETE' })

/**
 * 暂停任务
 * @param id 任务 ID
 */
export const pauseJob = (id: string) =>
  request<void>({ url: `${BASE}/${id}/pause`, method: 'POST' })

/**
 * 恢复任务
 * @param id 任务 ID
 */
export const resumeJob = (id: string) =>
  request<void>({ url: `${BASE}/${id}/resume`, method: 'POST' })

/**
 * 手动触发任务
 * @param id 任务 ID
 */
export const triggerJob = (id: string) =>
  request<void>({ url: `${BASE}/${id}/trigger`, method: 'POST' })

/**
 * 批量暂停任务
 * @param data 任务 ID 列表
 */
export const batchPauseJobs = (data: JobBatchRequest) =>
  request<void>({ url: `${BASE}/batch/pause`, method: 'POST', data })

/**
 * 批量恢复任务
 * @param data 任务 ID 列表
 */
export const batchResumeJobs = (data: JobBatchRequest) =>
  request<void>({ url: `${BASE}/batch/resume`, method: 'POST', data })

/**
 * 批量触发任务
 * @param data 任务 ID 列表
 */
export const batchTriggerJobs = (data: JobBatchRequest) =>
  request<void>({ url: `${BASE}/batch/trigger`, method: 'POST', data })

/**
 * 批量删除任务
 * @param data 任务 ID 列表
 */
export const batchDeleteJobs = (data: JobBatchRequest) =>
  request<void>({ url: `${BASE}/batch/delete`, method: 'POST', data })

// ==================== 执行日志 ====================

/**
 * 分页查询执行日志
 * @param params 日志分页与筛选条件
 * @returns 日志分页数据
 */
export const getJobLogPage = (params: JobLogPageQuery) =>
  request<PageData<JobLogVO>>({ url: `${BASE}/log/page`, method: 'GET', params, silent: true })

/**
 * 分页查询日志内容
 * @param params 日志内容分页条件
 * @returns 日志内容分页数据
 */
export const getJobLogContentPage = (params: JobLogContentPageQuery) =>
  request<PageData<JobLogContentVO>>({
    url: `${BASE}/log/content/page`,
    method: 'GET',
    params,
    silent: true,
  })

/**
 * 统计日志行数
 * @param logId 日志 ID
 * @returns 日志总行数
 */
export const getJobLogContentCount = (logId: string) =>
  request<number>({ url: `${BASE}/log/content/count`, method: 'GET', params: { logId }, silent: true })

/**
 * 构建 SSE 实时日志推送 URL（使用原生 EventSource 连接）
 * @param logId 日志 ID
 * @returns SSE 接口完整 URL
 */
export const buildLogContentStreamUrl = (logId: string): string => {
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  return `${baseURL}${BASE}/log/content/stream?logId=${encodeURIComponent(logId)}`
}

// ==================== 子任务（MapReduce） ====================

/**
 * 查询子任务列表（全量）
 * @param logId 日志 ID
 * @returns 子任务列表
 */
export const getJobTaskList = (logId: string) =>
  request<JobTaskVO[]>({ url: `${BASE}/task/list`, method: 'GET', params: { logId }, silent: true })

/**
 * 分页查询子任务
 * @param params 子任务分页条件
 * @returns 子任务分页数据
 */
export const getJobTaskPage = (params: JobTaskPageQuery) =>
  request<PageData<JobTaskVO>>({ url: `${BASE}/task/page`, method: 'GET', params, silent: true })

// ==================== GLUE 在线编码 ====================

/**
 * 保存 GLUE 代码
 * @param data GLUE 代码保存参数
 */
export const saveGlueCode = (data: GlueCodeSaveDTO) =>
  request<void>({ url: `${BASE}/glue/save`, method: 'POST', data })

/**
 * 获取最新版本 GLUE 代码
 * @param jobId 任务 ID
 * @returns 最新 GLUE 代码
 */
export const getLatestGlueCode = (jobId: string) =>
  request<GlueCodeVO>({ url: `${BASE}/glue/latest`, method: 'GET', params: { jobId }, silent: true })

/**
 * 获取 GLUE 版本列表
 * @param jobId 任务 ID
 * @returns GLUE 版本列表
 */
export const getGlueVersions = (jobId: string) =>
  request<GlueCodeVO[]>({ url: `${BASE}/glue/versions`, method: 'GET', params: { jobId }, silent: true })

/**
 * 回滚 GLUE 代码到指定版本
 * @param jobId 任务 ID
 * @param version 版本号
 */
export const rollbackGlueCode = (jobId: string, version: number) =>
  request<void>({ url: `${BASE}/glue/rollback`, method: 'POST', params: { jobId, version } })

// ==================== 版本管理 ====================

/**
 * 获取任务历史版本列表
 * @param jobId 任务 ID
 * @returns 历史版本列表
 */
export const getJobHistoryVersions = (jobId: string) =>
  request<JobHistoryVO[]>({ url: `${BASE}/history/versions`, method: 'GET', params: { jobId }, silent: true })

/**
 * 获取指定版本的任务详情
 * @param jobId 任务 ID
 * @param version 版本号
 * @returns 历史版本详情
 */
export const getJobHistoryDetail = (jobId: string, version: number) =>
  request<JobHistoryVO>({
    url: `${BASE}/history/detail`,
    method: 'GET',
    params: { jobId, version },
    silent: true,
  })

/**
 * 回滚任务到指定版本
 * @param jobId 任务 ID
 * @param version 版本号
 */
export const rollbackJobHistory = (jobId: string, version: number) =>
  request<void>({ url: `${BASE}/history/rollback`, method: 'POST', params: { jobId, version } })

/**
 * 对比两个版本差异
 * @param jobId 任务 ID
 * @param v1 版本号 1
 * @param v2 版本号 2
 * @returns 对比结果文本
 */
export const compareJobHistory = (jobId: string, v1: number, v2: number) =>
  request<string>({
    url: `${BASE}/history/compare`,
    method: 'GET',
    params: { jobId, v1, v2 },
    silent: true,
  })

// ==================== 统计趋势 ====================

/**
 * 每日统计
 * @param params 统计查询条件
 * @returns 每日统计列表
 */
export const getDailyStats = (params: JobDailyStatsQuery) =>
  request<JobDailyStatsVO[]>({ url: `${BASE}/stats/daily`, method: 'GET', params, silent: true })

/**
 * 汇总统计
 * @param params 统计查询条件
 * @returns 汇总统计
 */
export const getSummaryStats = (params: JobDailyStatsQuery) =>
  request<JobSummaryStatsVO>({ url: `${BASE}/stats/summary`, method: 'GET', params, silent: true })

// ==================== SLA 管理 ====================

/**
 * 分页查询 SLA
 * @param params 分页条件
 * @returns SLA 分页数据
 */
export const getSlaPage = (params: JobSlaPageQuery) =>
  request<PageData<JobSlaVO>>({ url: `${BASE}/sla/page`, method: 'GET', params, silent: true })

/**
 * 创建 SLA
 * @param data SLA 参数
 */
export const createSla = (data: JobSlaSaveDTO) =>
  request<void>({ url: `${BASE}/sla`, method: 'POST', data })

/**
 * 更新 SLA
 * @param data SLA 参数（含 id）
 */
export const updateSla = (data: JobSlaSaveDTO) =>
  request<void>({ url: `${BASE}/sla`, method: 'PUT', data })

/**
 * 删除 SLA
 * @param id SLA ID
 */
export const deleteSla = (id: string) =>
  request<void>({ url: `${BASE}/sla/${id}`, method: 'DELETE' })

/**
 * 启用/禁用 SLA
 * @param id SLA ID
 */
export const toggleSla = (id: string) =>
  request<void>({ url: `${BASE}/sla/${id}/toggle`, method: 'POST' })
