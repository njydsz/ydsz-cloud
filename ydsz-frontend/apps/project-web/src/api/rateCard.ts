/**
 * 项目计费卡 API 模块（前端）
 * <p>封装计费卡（{@code ydsz_project_rate_card}）CRUD 接口，对应后端 {@code /api/v1/project/rateCard/*} 端点。
 * <p>按角色/职级/技术栈定义计费标准，是计费/收入分摊的基础。
 * <p>供「项目管理 → 计费管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace RateCardApi {
  export interface RateCardVO {
    id: string;
    rateName: string;
    roleLevel: string;
    standardRate: number;
    overtimeRate: number;
    currency: string;
    effectiveDate: string;
    status: number;
    createTime: string;
  }

  export interface RateCardPageQuery {
    pageNum?: number;
    pageSize?: number;
    rateName?: string;
  }

  export interface RateCardDTO {
    rateName?: string;
    roleLevel?: string;
    standardRate?: number;
    overtimeRate?: number;
    currency?: string;
    effectiveDate?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getRateCardPageApi(params: RateCardApi.RateCardPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: RateCardApi.RateCardVO[];
  }>(`/api/v1/project/rate/card/page`, { params });
}

/** 查询全部列表 */
export function getRateCardListApi() {
  return requestClient.get<RateCardApi.RateCardVO[]>(`/api/v1/project/rate/card/list`);
}

/** 根据 ID 查询 */
export function getRateCardByIdApi(id: string) {
  return requestClient.get<RateCardApi.RateCardVO>(`/api/v1/project/rate/card/${id}`);
}

/** 创建 */
export function createRateCardApi(data: RateCardApi.RateCardDTO) {
  return requestClient.post<string>(`/api/v1/project/rate/card`, data);
}

/** 更新 */
export function updateRateCardApi(data: RateCardApi.RateCardDTO) {
  return requestClient.put<boolean>(`/api/v1/project/rate/card`, data);
}

/** 删除 */
export function deleteRateCardApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/rate/card/${id}`);
}
