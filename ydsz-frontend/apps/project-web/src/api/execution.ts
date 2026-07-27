import { requestClient } from '#/api/request';

export namespace ExecutionApi {
  export interface ExecutionVO {
    id: string;
    taskName: string;
    projectId: string;
    assignee: string;
    plannedStart: string;
    plannedEnd: string;
    actualStart: string;
    actualEnd: string;
    progress: number;
    status: number;
    createTime: string;
  }

  export interface ExecutionPageQuery {
    pageNum?: number;
    pageSize?: number;
    taskName?: string;
    projectId?: string;
  }

  export interface ExecutionDTO {
    taskName?: string;
    projectId?: string;
    assignee?: string;
    plannedStart?: string;
    plannedEnd?: string;
    actualStart?: string;
    actualEnd?: string;
    progress?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getExecutionPageApi(params: ExecutionApi.ExecutionPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ExecutionApi.ExecutionVO[];
  }>(`/api/v1/project/execution/wbs/task/page`, { params });
}

/** 查询全部列表 */
export function getExecutionListApi() {
  return requestClient.get<ExecutionApi.ExecutionVO[]>(`/api/v1/project/execution/wbs/task/list`);
}

/** 根据 ID 查询 */
export function getExecutionByIdApi(id: string) {
  return requestClient.get<ExecutionApi.ExecutionVO>(`/api/v1/project/execution/wbs/task/${id}`);
}

/** 创建 */
export function createExecutionApi(data: ExecutionApi.ExecutionDTO) {
  return requestClient.post<string>(`/api/v1/project/execution/wbs/task`, data);
}

/** 更新 */
export function updateExecutionApi(data: ExecutionApi.ExecutionDTO) {
  return requestClient.put<boolean>(`/api/v1/project/execution/wbs/task`, data);
}

/** 删除 */
export function deleteExecutionApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/execution/wbs/task/${id}`);
}
