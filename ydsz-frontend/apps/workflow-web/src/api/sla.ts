import { requestClient } from '#/api/request';

export namespace SlaApi {
  export interface SlaVO {
    id: string;
    slaName: string;
    templateId: string;
    maxDuration: number;
    warnThreshold: number;
    status: number;
    createTime: string;
  }

  export interface SlaPageQuery {
    pageNum?: number;
    pageSize?: number;
    slaName?: string;
  }

  export interface SlaDTO {
    slaName?: string;
    templateId?: string;
    maxDuration?: number;
    warnThreshold?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getSlaPageApi(params: SlaApi.SlaPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: SlaApi.SlaVO[];
  }>(`/workflow/engine/page`, { params });
}

/** 查询全部列表 */
export function getSlaListApi() {
  return requestClient.get<SlaApi.SlaVO[]>(`/workflow/engine/list`);
}

/** 根据 ID 查询 */
export function getSlaByIdApi(id: string) {
  return requestClient.get<SlaApi.SlaVO>(`/workflow/engine/${id}`);
}

/** 创建 */
export function createSlaApi(data: SlaApi.SlaDTO) {
  return requestClient.post<string>(`/workflow/engine`, data);
}

/** 更新 */
export function updateSlaApi(data: SlaApi.SlaDTO) {
  return requestClient.put<boolean>(`/workflow/engine`, data);
}

/** 删除 */
export function deleteSlaApi(id: string) {
  return requestClient.delete<boolean>(`/workflow/engine/${id}`);
}
