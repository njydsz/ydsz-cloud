import { requestClient } from '#/api/request';

export namespace JobLogApi {
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

  export interface JobLogPageQuery {
    pageNum?: number;
    pageSize?: number;
    jobName?: string;
  }

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
