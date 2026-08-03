/**
 * 定时任务 API 模块（前端）
 * <p>封装定时任务（{@code ydsz_job}）的 CRUD 接口调用，对应后端 {@code /api/v1/cronjob/job/*} 端点。
 * <p>支持 Cron 表达式、负责人、告警通道、超时配置、并发策略等。
 * <p>供「任务调度 → 任务列表」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace JobApi {
  /** 定时任务视图对象 */
  export interface JobVO {
    id: string;
    jobName: string;
    jobGroup: string;
    cronExpression: string;
    jobType: string;
    executorHandler: string;
    executorParam: string;
    status: number;
    createTime: string;
  }

  /** 定时任务分页查询参数 */
  export interface JobPageQuery {
    pageNum?: number;
    pageSize?: number;
    jobName?: string;
    jobGroup?: string;
  }

  /** 定时任务创建/更新请求参数 */
  export interface JobDTO {
    jobName?: string;
    jobGroup?: string;
    cronExpression?: string;
    jobType?: string;
    executorHandler?: string;
    executorParam?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getJobPageApi(params: JobApi.JobPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: JobApi.JobVO[];
  }>(`/api/v1/cronjob/page`, { params });
}

/** 查询全部列表 */
export function getJobListApi() {
  return requestClient.get<JobApi.JobVO[]>(`/api/v1/cronjob/list`);
}

/** 根据 ID 查询 */
export function getJobByIdApi(id: string) {
  return requestClient.get<JobApi.JobVO>(`/api/v1/cronjob/${id}`);
}

/** 创建 */
export function createJobApi(data: JobApi.JobDTO) {
  return requestClient.post<string>(`/api/v1/cronjob`, data);
}

/** 更新 */
export function updateJobApi(data: JobApi.JobDTO) {
  return requestClient.put<boolean>(`/api/v1/cronjob`, data);
}

/** 删除 */
export function deleteJobApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/cronjob/${id}`);
}
