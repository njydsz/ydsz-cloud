import { requestClient } from '#/api/request';

export namespace DagApi {
  export interface DagVO {
    id: string;
    dagName: string;
    dagConfig: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface DagPageQuery {
    pageNum?: number;
    pageSize?: number;
    dagName?: string;
  }

  export interface DagDTO {
    dagName?: string;
    dagConfig?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getDagPageApi(params: DagApi.DagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DagApi.DagVO[];
  }>(`/agent/dag/page`, { params });
}

/** 查询全部列表 */
export function getDagListApi() {
  return requestClient.get<DagApi.DagVO[]>(`/agent/dag/list`);
}

/** 根据 ID 查询 */
export function getDagByIdApi(id: string) {
  return requestClient.get<DagApi.DagVO>(`/agent/dag/${id}`);
}

/** 创建 */
export function createDagApi(data: DagApi.DagDTO) {
  return requestClient.post<string>(`/agent/dag`, data);
}

/** 更新 */
export function updateDagApi(data: DagApi.DagDTO) {
  return requestClient.put<boolean>(`/agent/dag`, data);
}

/** 删除 */
export function deleteDagApi(id: string) {
  return requestClient.delete<boolean>(`/agent/dag/${id}`);
}
