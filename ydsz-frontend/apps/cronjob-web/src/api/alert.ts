import { requestClient } from '#/api/request';

export namespace AlertApi {
  export interface AlertVO {
    id: string;
    alertName: string;
    alertType: string;
    alertLevel: string;
    condition: string;
    notifyChannels: string;
    status: number;
    createTime: string;
  }

  export interface AlertPageQuery {
    pageNum?: number;
    pageSize?: number;
    alertName?: string;
  }

  export interface AlertDTO {
    alertName?: string;
    alertType?: string;
    alertLevel?: string;
    condition?: string;
    notifyChannels?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getAlertPageApi(params: AlertApi.AlertPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: AlertApi.AlertVO[];
  }>(`/cronjob/alert/page`, { params });
}

/** 查询全部列表 */
export function getAlertListApi() {
  return requestClient.get<AlertApi.AlertVO[]>(`/cronjob/alert/list`);
}

/** 根据 ID 查询 */
export function getAlertByIdApi(id: string) {
  return requestClient.get<AlertApi.AlertVO>(`/cronjob/alert/${id}`);
}

/** 创建 */
export function createAlertApi(data: AlertApi.AlertDTO) {
  return requestClient.post<string>(`/cronjob/alert`, data);
}

/** 更新 */
export function updateAlertApi(data: AlertApi.AlertDTO) {
  return requestClient.put<boolean>(`/cronjob/alert`, data);
}

/** 删除 */
export function deleteAlertApi(id: string) {
  return requestClient.delete<boolean>(`/cronjob/alert/${id}`);
}
