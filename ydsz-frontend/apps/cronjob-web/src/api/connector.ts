/**
 * 任务连接器 API 模块（前端）
 * <p>封装任务执行器（{@code ydsz_job_connector}）接口，对应后端 {@code /api/v1/cronjob/connector/*} 端点。
 * <p>支持 HTTP、Shell、SQL、Java、Kettle、Python 等多种任务类型的执行器注册。
 * <p>供「任务调度 → 执行器管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  }>(`/api/v1/cronjob/connector/page`, { params });
}

/** 查询全部列表 */
export function getConnectorListApi() {
  return requestClient.get<ConnectorApi.ConnectorVO[]>(`/api/v1/cronjob/connector/list`);
}

/** 根据 ID 查询 */
export function getConnectorByIdApi(id: string) {
  return requestClient.get<ConnectorApi.ConnectorVO>(`/api/v1/cronjob/connector/${id}`);
}

/** 创建 */
export function createConnectorApi(data: ConnectorApi.ConnectorDTO) {
  return requestClient.post<string>(`/api/v1/cronjob/connector`, data);
}

/** 更新 */
export function updateConnectorApi(data: ConnectorApi.ConnectorDTO) {
  return requestClient.put<boolean>(`/api/v1/cronjob/connector`, data);
}

/** 删除 */
export function deleteConnectorApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/cronjob/connector/${id}`);
}
