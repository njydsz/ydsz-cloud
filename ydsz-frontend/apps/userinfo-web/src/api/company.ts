import { requestClient } from '#/api/request';

export namespace CompanyApi {
  export interface CompanyVO {
    id: string;
    companyName: string;
    legalPerson?: string;
    contactPhone?: string;
    contactEmail?: string;
    address?: string;
    status: number;
    remark?: string;
    createTime?: string;
  }

  export interface CompanySaveDTO {
    id?: string;
    companyName: string;
    legalPerson?: string;
    contactPhone?: string;
    contactEmail?: string;
    address?: string;
    status?: number;
    remark?: string;
  }
}

/** 查询全部公司列表 */
export function getCompanyListApi() {
  return requestClient.get<CompanyApi.CompanyVO[]>('/api/v1/company/list');
}

/** 根据 ID 查询公司 */
export function getCompanyByIdApi(id: string) {
  return requestClient.get<CompanyApi.CompanyVO>(`/api/v1/company/${id}`);
}

/** 创建公司 */
export function createCompanyApi(data: CompanyApi.CompanySaveDTO) {
  return requestClient.post<string>('/api/v1/company', data);
}

/** 更新公司 */
export function updateCompanyApi(data: CompanyApi.CompanySaveDTO) {
  return requestClient.put<boolean>('/api/v1/company', data);
}

/** 删除公司 */
export function deleteCompanyApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/company/${id}`);
}
