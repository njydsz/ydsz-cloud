import { requestClient } from '#/api/request';

export namespace TaskApi {
  export interface TaskVO {
    id: string;
    taskName: string;
    processInstanceId: string;
    assignee: string;
    createTime: string;
    dueDate: string;
    status: string;
  }

  export interface TaskPageQuery {
    pageNum?: number;
    pageSize?: number;
    taskName?: string;
    assignee?: string;
  }

  export interface TaskDTO {
    processInstanceId?: string;
    assignee?: string;
  }
}

/** 分页查询 */
export function getTaskPageApi(params: TaskApi.TaskPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: TaskApi.TaskVO[];
  }>(`/workflow/engine/page`, { params });
}

/** 查询全部列表 */
export function getTaskListApi() {
  return requestClient.get<TaskApi.TaskVO[]>(`/workflow/engine/list`);
}

/** 根据 ID 查询 */
export function getTaskByIdApi(id: string) {
  return requestClient.get<TaskApi.TaskVO>(`/workflow/engine/${id}`);
}

/** 创建 */
export function createTaskApi(data: TaskApi.TaskDTO) {
  return requestClient.post<string>(`/workflow/engine`, data);
}

/** 更新 */
export function updateTaskApi(data: TaskApi.TaskDTO) {
  return requestClient.put<boolean>(`/workflow/engine`, data);
}

/** 删除 */
export function deleteTaskApi(id: string) {
  return requestClient.delete<boolean>(`/workflow/engine/${id}`);
}
