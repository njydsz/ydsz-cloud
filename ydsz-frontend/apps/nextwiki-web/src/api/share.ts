import { requestClient } from '#/api/request';

export namespace ShareApi {
  export interface ShareVO {
    id: string;
    fileName: string;
    shareTo: string;
    permission: string;
    expireDate: string;
    status: number;
    createTime: string;
  }

  export interface SharePageQuery {
    pageNum?: number;
    pageSize?: number;
    fileName?: string;
  }

  export interface ShareDTO {
    fileId?: string;
    shareTo?: string;
    permission?: string;
    expireDate?: string;
  }
}

/** 分页查询 */
export function getSharePageApi(params: ShareApi.SharePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ShareApi.ShareVO[];
  }>(`/api/v1/nextwiki/shares/page`, { params });
}

/** 查询全部列表 */
export function getShareListApi() {
  return requestClient.get<ShareApi.ShareVO[]>(`/api/v1/nextwiki/shares/list`);
}

/** 根据 ID 查询 */
export function getShareByIdApi(id: string) {
  return requestClient.get<ShareApi.ShareVO>(`/api/v1/nextwiki/shares/${id}`);
}

/** 创建 */
export function createShareApi(data: ShareApi.ShareDTO) {
  return requestClient.post<string>(`/api/v1/nextwiki/shares`, data);
}

/** 更新 */
export function updateShareApi(data: ShareApi.ShareDTO) {
  return requestClient.put<boolean>(`/api/v1/nextwiki/shares`, data);
}

/** 删除 */
export function deleteShareApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/nextwiki/shares/${id}`);
}
