import { requestClient } from '#/api/request';

export namespace PreferenceApi {
  export interface PreferenceVO {
    id: string;
    userId: string;
    channel: string;
    dndEnabled: number;
    dndStart: string;
    dndEnd: string;
    status: number;
    createTime: string;
  }

  export interface PreferencePageQuery {
    pageNum?: number;
    pageSize?: number;
    userId?: string;
  }

  export interface PreferenceDTO {
    userId?: string;
    channel?: string;
    dndEnabled?: number;
    dndStart?: string;
    dndEnd?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getPreferencePageApi(params: PreferenceApi.PreferencePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: PreferenceApi.PreferenceVO[];
  }>(`/message/preference/page`, { params });
}

/** 查询全部列表 */
export function getPreferenceListApi() {
  return requestClient.get<PreferenceApi.PreferenceVO[]>(`/message/preference/list`);
}

/** 根据 ID 查询 */
export function getPreferenceByIdApi(id: string) {
  return requestClient.get<PreferenceApi.PreferenceVO>(`/message/preference/${id}`);
}

/** 创建 */
export function createPreferenceApi(data: PreferenceApi.PreferenceDTO) {
  return requestClient.post<string>(`/message/preference`, data);
}

/** 更新 */
export function updatePreferenceApi(data: PreferenceApi.PreferenceDTO) {
  return requestClient.put<boolean>(`/message/preference`, data);
}

/** 删除 */
export function deletePreferenceApi(id: string) {
  return requestClient.delete<boolean>(`/message/preference/${id}`);
}
