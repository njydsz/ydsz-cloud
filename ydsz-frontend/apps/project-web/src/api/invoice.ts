import { requestClient } from '#/api/request';

export namespace InvoiceApi {
  export interface InvoiceVO {
    id: string;
    invoiceCode: string;
    projectId: string;
    customerName: string;
    invoiceAmount: number;
    invoiceDate: string;
    invoiceType: string;
    status: number;
    createTime: string;
  }

  export interface InvoicePageQuery {
    pageNum?: number;
    pageSize?: number;
    invoiceCode?: string;
  }

  export interface InvoiceDTO {
    invoiceCode?: string;
    projectId?: string;
    customerName?: string;
    invoiceAmount?: number;
    invoiceDate?: string;
    invoiceType?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getInvoicePageApi(params: InvoiceApi.InvoicePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: InvoiceApi.InvoiceVO[];
  }>(`/api/v1/project/project/invoice/page`, { params });
}

/** 查询全部列表 */
export function getInvoiceListApi() {
  return requestClient.get<InvoiceApi.InvoiceVO[]>(`/api/v1/project/project/invoice/list`);
}

/** 根据 ID 查询 */
export function getInvoiceByIdApi(id: string) {
  return requestClient.get<InvoiceApi.InvoiceVO>(`/api/v1/project/project/invoice/${id}`);
}

/** 创建 */
export function createInvoiceApi(data: InvoiceApi.InvoiceDTO) {
  return requestClient.post<string>(`/api/v1/project/project/invoice`, data);
}

/** 更新 */
export function updateInvoiceApi(data: InvoiceApi.InvoiceDTO) {
  return requestClient.put<boolean>(`/api/v1/project/project/invoice`, data);
}

/** 删除 */
export function deleteInvoiceApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/project/invoice/${id}`);
}
