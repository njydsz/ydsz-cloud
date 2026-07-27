import { requestClient } from '#/api/request';

export namespace ContractApi {
  export interface ContractVO {
    id: string;
    contractCode: string;
    contractName: string;
    customerName: string;
    contractAmount: number;
    contractType: string;
    signDate: string;
    startDate: string;
    endDate: string;
    status: number;
    createTime: string;
  }

  export interface ContractPageQuery {
    pageNum?: number;
    pageSize?: number;
    contractName?: string;
    contractCode?: string;
  }

  export interface ContractDTO {
    contractCode?: string;
    contractName?: string;
    customerName?: string;
    contractAmount?: number;
    contractType?: string;
    signDate?: string;
    startDate?: string;
    endDate?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getContractPageApi(params: ContractApi.ContractPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ContractApi.ContractVO[];
  }>(`/api/v1/project/project/contract/page`, { params });
}

/** 查询全部列表 */
export function getContractListApi() {
  return requestClient.get<ContractApi.ContractVO[]>(`/api/v1/project/project/contract/list`);
}

/** 根据 ID 查询 */
export function getContractByIdApi(id: string) {
  return requestClient.get<ContractApi.ContractVO>(`/api/v1/project/project/contract/${id}`);
}

/** 创建 */
export function createContractApi(data: ContractApi.ContractDTO) {
  return requestClient.post<string>(`/api/v1/project/project/contract`, data);
}

/** 更新 */
export function updateContractApi(data: ContractApi.ContractDTO) {
  return requestClient.put<boolean>(`/api/v1/project/project/contract`, data);
}

/** 删除 */
export function deleteContractApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/project/contract/${id}`);
}
