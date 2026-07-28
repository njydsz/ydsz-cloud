/**
 * 消息偏好 API 模块（前端）
 * <p>封装用户消息偏好设置接口，对应后端 {@code /api/v1/message/preference/*} 端点。
 * <p>支持按渠道订阅/退订、免打扰时段、消息类型过滤。
 * <p>供「个人中心 → 通知设置」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
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
  }>(`/api/v1/message/preference/page`, { params });
}

/** 查询全部列表 */
export function getPreferenceListApi() {
  return requestClient.get<PreferenceApi.PreferenceVO[]>(`/api/v1/message/preference/list`);
}

/** 根据 ID 查询 */
export function getPreferenceByIdApi(id: string) {
  return requestClient.get<PreferenceApi.PreferenceVO>(`/api/v1/message/preference/${id}`);
}

/** 创建 */
export function createPreferenceApi(data: PreferenceApi.PreferenceDTO) {
  return requestClient.post<string>(`/api/v1/message/preference`, data);
}

/** 更新 */
export function updatePreferenceApi(data: PreferenceApi.PreferenceDTO) {
  return requestClient.put<boolean>(`/api/v1/message/preference`, data);
}

/** 删除 */
export function deletePreferenceApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/message/preference/${id}`);
}
