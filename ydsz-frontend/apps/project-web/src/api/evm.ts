/**
 * EVM 挣值管理 API 模块（前端）
 * <p>封装 EVM（Earned Value Management）指标接口，对应后端 {@code /api/v1/project/evm/*} 端点。
 * <p>提供 PV/EV/AC/CPI/SPI 等挣值分析指标，支持项目健康度评分。
 * <p>供「项目管理 → EVM 分析」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace EvmApi {
  /** EVM 挣值指标视图对象 */
  export interface EvmVO {
    id: string;
    projectId: string;
    measureDate: string;
    pv: number;
    ev: number;
    ac: number;
    sv: number;
    cv: number;
    spi: number;
    cpi: number;
    createTime: string;
  }

  /** EVM 指标分页查询参数 */
  export interface EvmPageQuery {
    pageNum?: number;
    pageSize?: number;
    projectId?: string;
  }

  /** EVM 指标创建/更新请求参数 */
  export interface EvmDTO {
    projectId?: string;
    measureDate?: string;
    pv?: number;
    ev?: number;
    ac?: number;
  }
}

/** 分页查询 */
export function getEvmPageApi(params: EvmApi.EvmPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: EvmApi.EvmVO[];
  }>(`/api/v1/project/evm/measure/page`, { params });
}

/** 查询全部列表 */
export function getEvmListApi() {
  return requestClient.get<EvmApi.EvmVO[]>(`/api/v1/project/evm/measure/list`);
}

/** 根据 ID 查询 */
export function getEvmByIdApi(id: string) {
  return requestClient.get<EvmApi.EvmVO>(`/api/v1/project/evm/measure/${id}`);
}

/** 创建 */
export function createEvmApi(data: EvmApi.EvmDTO) {
  return requestClient.post<string>(`/api/v1/project/evm/measure`, data);
}

/** 更新 */
export function updateEvmApi(data: EvmApi.EvmDTO) {
  return requestClient.put<boolean>(`/api/v1/project/evm/measure`, data);
}

/** 删除 */
export function deleteEvmApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/evm/measure/${id}`);
}
