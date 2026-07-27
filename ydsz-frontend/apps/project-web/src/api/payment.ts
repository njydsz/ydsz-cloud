import { requestClient } from '#/api/request';

export namespace PaymentApi {
  export interface PaymentVO {
    id: string;
    projectId: string;
    contractId: string;
    paymentAmount: number;
    paymentDate: string;
    paymentMethod: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface PaymentPageQuery {
    pageNum?: number;
    pageSize?: number;
    projectId?: string;
  }

  export interface PaymentDTO {
    projectId?: string;
    contractId?: string;
    paymentAmount?: number;
    paymentDate?: string;
    paymentMethod?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getPaymentPageApi(params: PaymentApi.PaymentPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: PaymentApi.PaymentVO[];
  }>(`/api/v1/project/project/payment/page`, { params });
}

/** 查询全部列表 */
export function getPaymentListApi() {
  return requestClient.get<PaymentApi.PaymentVO[]>(`/api/v1/project/project/payment/list`);
}

/** 根据 ID 查询 */
export function getPaymentByIdApi(id: string) {
  return requestClient.get<PaymentApi.PaymentVO>(`/api/v1/project/project/payment/${id}`);
}

/** 创建 */
export function createPaymentApi(data: PaymentApi.PaymentDTO) {
  return requestClient.post<string>(`/api/v1/project/project/payment`, data);
}

/** 更新 */
export function updatePaymentApi(data: PaymentApi.PaymentDTO) {
  return requestClient.put<boolean>(`/api/v1/project/project/payment`, data);
}

/** 删除 */
export function deletePaymentApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/project/payment/${id}`);
}
