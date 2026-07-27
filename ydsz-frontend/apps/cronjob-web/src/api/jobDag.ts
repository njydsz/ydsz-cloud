import { requestClient } from '#/api/request';

export namespace JobDagApi {
  export interface JobDagVO {
    id: string;
    dagName: string;
    dagCode: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface JobDagPageQuery {
    pageNum?: number;
    pageSize?: number;
    dagName?: string;
  }

  export interface JobDagDTO {
    dagName?: string;
    dagCode?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getJobDagPageApi(params: JobDagApi.JobDagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: JobDagApi.JobDagVO[];
  }>(`/cronjob/dag/page`, { params });
}

/** 查询全部列表 */
export function getJobDagListApi() {
  return requestClient.get<JobDagApi.JobDagVO[]>(`/cronjob/dag/list`);
}

/** 根据 ID 查询 */
export function getJobDagByIdApi(id: string) {
  return requestClient.get<JobDagApi.JobDagVO>(`/cronjob/dag/${id}`);
}

/** 创建 */
export function createJobDagApi(data: JobDagApi.JobDagDTO) {
  return requestClient.post<string>(`/cronjob/dag`, data);
}

/** 更新 */
export function updateJobDagApi(data: JobDagApi.JobDagDTO) {
  return requestClient.put<boolean>(`/cronjob/dag`, data);
}

/** 删除 */
export function deleteJobDagApi(id: string) {
  return requestClient.delete<boolean>(`/cronjob/dag/${id}`);
}
