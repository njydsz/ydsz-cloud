import { requestClient } from '#/api/request';

export namespace ConnectorApi {
  export interface ConnectorVO {
    id: string;
    connectorName: string;
    connectorType: string;
    endpoint: string;
    authType: string;
    status: number;
    createTime: string;
  }

  export interface ConnectorPageQuery {
    pageNum?: number;
    pageSize?: number;
    connectorName?: string;
  }

  export interface ConnectorDTO {
    connectorName?: string;
    connectorType?: string;
    endpoint?: string;
    authType?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getConnectorPageApi(params: ConnectorApi.ConnectorPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ConnectorApi.ConnectorVO[];
  }>(`/cronjob/connector/page`, { params });
}

/** 查询全部列表 */
export function getConnectorListApi() {
  return requestClient.get<ConnectorApi.ConnectorVO[]>(`/cronjob/connector/list`);
}

/** 根据 ID 查询 */
export function getConnectorByIdApi(id: string) {
  return requestClient.get<ConnectorApi.ConnectorVO>(`/cronjob/connector/${id}`);
}

/** 创建 */
export function createConnectorApi(data: ConnectorApi.ConnectorDTO) {
  return requestClient.post<string>(`/cronjob/connector`, data);
}

/** 更新 */
export function updateConnectorApi(data: ConnectorApi.ConnectorDTO) {
  return requestClient.put<boolean>(`/cronjob/connector`, data);
}

/** 删除 */
export function deleteConnectorApi(id: string) {
  return requestClient.delete<boolean>(`/cronjob/connector/${id}`);
}
