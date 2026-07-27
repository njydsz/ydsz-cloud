import { requestClient } from '#/api/request';

export namespace InstanceApi {
  export interface InstanceVO {
    id: string;
    processInstanceId: string;
    templateName: string;
    starter: string;
    currentTask: string;
    currentAssignee: string;
    status: string;
    startTime: string;
    createTime: string;
  }

  export interface InstancePageQuery {
    pageNum?: number;
    pageSize?: number;
    processInstanceId?: string;
    status?: string;
  }

  export interface InstanceDTO {
    templateId?: string;
    starter?: string;
  }
}

/** 分页查询 */
export function getInstancePageApi(params: InstanceApi.InstancePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: InstanceApi.InstanceVO[];
  }>(`/workflow/engine/page`, { params });
}

/** 查询全部列表 */
export function getInstanceListApi() {
  return requestClient.get<InstanceApi.InstanceVO[]>(`/workflow/engine/list`);
}

/** 根据 ID 查询 */
export function getInstanceByIdApi(id: string) {
  return requestClient.get<InstanceApi.InstanceVO>(`/workflow/engine/${id}`);
}

/** 创建 */
export function createInstanceApi(data: InstanceApi.InstanceDTO) {
  return requestClient.post<string>(`/workflow/engine`, data);
}

/** 更新 */
export function updateInstanceApi(data: InstanceApi.InstanceDTO) {
  return requestClient.put<boolean>(`/workflow/engine`, data);
}

/** 删除 */
export function deleteInstanceApi(id: string) {
  return requestClient.delete<boolean>(`/workflow/engine/${id}`);
}
