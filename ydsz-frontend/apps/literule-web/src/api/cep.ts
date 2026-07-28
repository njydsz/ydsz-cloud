/**
 * 规则 CEP 复杂事件 API 模块（前端）
 * <p>封装复杂事件处理（Complex Event Processing）规则接口，对应后端 {@code /api/v1/literule/cep/*} 端点。
 * <p>支持事件流模式匹配、滑动时间窗、事件序列检测。
 * <p>供「规则引擎 → CEP 规则」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace CepApi {
  export interface CepVO {
    id: string;
    cepName: string;
    cepPattern: string;
    windowSize: number;
    description: string;
    status: number;
    createTime: string;
  }

  export interface CepPageQuery {
    pageNum?: number;
    pageSize?: number;
    cepName?: string;
  }

  export interface CepDTO {
    cepName?: string;
    cepPattern?: string;
    windowSize?: number;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getCepPageApi(params: CepApi.CepPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: CepApi.CepVO[];
  }>(`/api/v1/literule/cep/page`, { params });
}

/** 查询全部列表 */
export function getCepListApi() {
  return requestClient.get<CepApi.CepVO[]>(`/api/v1/literule/cep/list`);
}

/** 根据 ID 查询 */
export function getCepByIdApi(id: string) {
  return requestClient.get<CepApi.CepVO>(`/api/v1/literule/cep/${id}`);
}

/** 创建 */
export function createCepApi(data: CepApi.CepDTO) {
  return requestClient.post<string>(`/api/v1/literule/cep`, data);
}

/** 更新 */
export function updateCepApi(data: CepApi.CepDTO) {
  return requestClient.put<boolean>(`/api/v1/literule/cep`, data);
}

/** 删除 */
export function deleteCepApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/literule/cep/${id}`);
}
