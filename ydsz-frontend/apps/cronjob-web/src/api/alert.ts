/**
 * 任务告警 API 模块（前端）
 * <p>封装任务告警规则与告警日志接口，对应后端 {@code /api/v1/cronjob/alert/*} 端点。
 * <p>支持告警通道（邮件/短信/企微/钉钉）、告警抑制、告警风暴收敛。
 * <p>供「任务调度 → 告警配置」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  }>(`/api/v1/cronjob/alert/page`, { params });
}

/** 查询全部列表 */
export function getAlertListApi() {
  return requestClient.get<AlertApi.AlertVO[]>(`/api/v1/cronjob/alert/list`);
}

/** 根据 ID 查询 */
export function getAlertByIdApi(id: string) {
  return requestClient.get<AlertApi.AlertVO>(`/api/v1/cronjob/alert/${id}`);
}

/** 创建 */
export function createAlertApi(data: AlertApi.AlertDTO) {
  return requestClient.post<string>(`/api/v1/cronjob/alert`, data);
}

/** 更新 */
export function updateAlertApi(data: AlertApi.AlertDTO) {
  return requestClient.put<boolean>(`/api/v1/cronjob/alert`, data);
}

/** 删除 */
export function deleteAlertApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/cronjob/alert/${id}`);
}
