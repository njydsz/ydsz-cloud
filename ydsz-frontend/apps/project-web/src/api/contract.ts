/**
 * 项目合同 API 模块（前端）
 * <p>封装项目合同（{@code ydsz_project_contract}）CRUD 接口，对应后端 {@code /api/v1/project/contract/*} 端点。
 * <p>支持主合同/补充协议/变更单，记录金额、税率、收款条件、履约期限。
 * <p>供「项目管理 → 合同管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace ContractApi {
  /** 项目合同视图对象 */
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

  /** 合同分页查询参数 */
  export interface ContractPageQuery {
    pageNum?: number;
    pageSize?: number;
    contractName?: string;
    contractCode?: string;
  }

  /** 合同创建/更新请求参数 */
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
