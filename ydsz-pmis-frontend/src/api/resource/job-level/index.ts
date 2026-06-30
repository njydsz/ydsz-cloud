import { request } from '@/utils/request'
import type { JobLevelVO, JobLevelRateVO } from './types'

/** 所有职级 (L1-L18) */
export const listJobLevels = () =>
  request<JobLevelVO[]>({ url: '/job-levels', method: 'GET' })

/** 查询生效的职级费率 */
export const getJobLevelRate = (levelCode: string, date?: string) =>
  request<JobLevelRateVO>({ url: '/job-levels/rate', method: 'GET', params: { levelCode, date } })

/** 查询某职级所有费率版本 */
export const listJobLevelRateVersions = (levelCode: string) =>
  request<JobLevelRateVO[]>({ url: '/job-levels/rate/versions', method: 'GET', params: { levelCode } })
