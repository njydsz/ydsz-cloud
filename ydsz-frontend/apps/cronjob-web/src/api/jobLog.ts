/**
 * 任务执行日志 API 模块（前端）
 * <p>封装任务执行日志（{@code ydsz_job_log}）查询接口，对应后端 {@code /api/v1/cronjob/jobLog/*} 端点。
 * <p>记录每次调度的开始时间、结束时间、状态、返回值、异常堆栈。
 * <p>供「任务调度 → 执行历史」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace JobLogApi {
  /** 任务执行日志视图对象 */
  export interface JobLogVO {
    id: string;
    jobId: string;
    jobName: string;
    jobGroup: string;
    triggerTime: string;
    triggerCode: number;
    handleTime: string;
    handleCode: number;
    handleMsg: string;
  }

  /** 执行日志分页查询参数 */
  export interface JobLogPageQuery {
    pageNum?: number;
    pageSize?: number;
    jobName?: string;
  }

  /** 执行日志创建/更新请求参数 */
  export interface JobLogDTO {
    jobId?: string;
    jobName?: string;
  }
}

/** 分页查询 */
export function getJobLogPageApi(params: JobLogApi.JobLogPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: JobLogApi.JobLogVO[];
  }>(`/api/v1/cronjob/log/page`, { params });
}

/** 查询全部列表 */
export function getJobLogListApi() {
  return requestClient.get<JobLogApi.JobLogVO[]>(`/api/v1/cronjob/log/list`);
}

/** 根据 ID 查询 */
export function getJobLogByIdApi(id: string) {
  return requestClient.get<JobLogApi.JobLogVO>(`/api/v1/cronjob/log/${id}`);
}

/** 创建 */
export function createJobLogApi(data: JobLogApi.JobLogDTO) {
  return requestClient.post<string>(`/api/v1/cronjob/log`, data);
}

/** 更新 */
export function updateJobLogApi(data: JobLogApi.JobLogDTO) {
  return requestClient.put<boolean>(`/api/v1/cronjob/log`, data);
}

/** 删除 */
export function deleteJobLogApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/cronjob/log/${id}`);
}
