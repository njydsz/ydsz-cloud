import { requestClient } from '#/api/request';

export namespace JobApi {
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

  export interface JobPageQuery {
    pageNum?: number;
    pageSize?: number;
    jobName?: string;
    jobGroup?: string;
  }

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
  }>(`/cronjob/page`, { params });
}

/** 查询全部列表 */
export function getJobListApi() {
  return requestClient.get<JobApi.JobVO[]>(`/cronjob/list`);
}

/** 根据 ID 查询 */
export function getJobByIdApi(id: string) {
  return requestClient.get<JobApi.JobVO>(`/cronjob/${id}`);
}

/** 创建 */
export function createJobApi(data: JobApi.JobDTO) {
  return requestClient.post<string>(`/cronjob`, data);
}

/** 更新 */
export function updateJobApi(data: JobApi.JobDTO) {
  return requestClient.put<boolean>(`/cronjob`, data);
}

/** 删除 */
export function deleteJobApi(id: string) {
  return requestClient.delete<boolean>(`/cronjob/${id}`);
}
