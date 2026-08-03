/**
 * 项目发票 API 模块（前端）
 * <p>封装项目发票（{@code ydsz_project_invoice}）CRUD 接口，对应后端 {@code /api/v1/project/invoice/*} 端点。
 * <p>记录开票申请、发票号、税率、金额、与回款/合同的关联。
 * <p>供「项目管理 → 发票管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace InvoiceApi {
  /** 项目发票视图对象 */
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

  /** 发票分页查询参数 */
  export interface InvoicePageQuery {
    pageNum?: number;
    pageSize?: number;
    invoiceCode?: string;
  }

  /** 发票创建/更新请求参数 */
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
