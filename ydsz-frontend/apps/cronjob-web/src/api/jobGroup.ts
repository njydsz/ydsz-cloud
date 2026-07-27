import { requestClient } from '#/api/request';

export namespace JobGroupApi {
  export interface JobGroupVO {
    id: string;
    groupName: string;
    appname: string;
    addressList: string;
    status: number;
    createTime: string;
  }

  export interface JobGroupPageQuery {
    pageNum?: number;
    pageSize?: number;
    groupName?: string;
  }

  export interface JobGroupDTO {
    groupName?: string;
    appname?: string;
    addressList?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getJobGroupPageApi(params: JobGroupApi.JobGroupPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: JobGroupApi.JobGroupVO[];
  }>(`/api/v1/cronjob/group/page`, { params });
}

/** 查询全部列表 */
export function getJobGroupListApi() {
  return requestClient.get<JobGroupApi.JobGroupVO[]>(`/api/v1/cronjob/group/list`);
}

/** 根据 ID 查询 */
export function getJobGroupByIdApi(id: string) {
  return requestClient.get<JobGroupApi.JobGroupVO>(`/api/v1/cronjob/group/${id}`);
}

/** 创建 */
export function createJobGroupApi(data: JobGroupApi.JobGroupDTO) {
  return requestClient.post<string>(`/api/v1/cronjob/group`, data);
}

/** 更新 */
export function updateJobGroupApi(data: JobGroupApi.JobGroupDTO) {
  return requestClient.put<boolean>(`/api/v1/cronjob/group`, data);
}

/** 删除 */
export function deleteJobGroupApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/cronjob/group/${id}`);
}
