/**
 * 项目立项 API 模块（前端）
 * <p>封装项目立项（{@code ydsz_project_initiation}）CRUD 接口，对应后端 {@code /api/v1/project/initiation/*} 端点。
 * <p>支持立项申请、审批、变更、作废全流程。
 * <p>供「项目管理 → 立项管理」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace InitiationApi {
  /** 项目立项视图对象 */
  export interface InitiationVO {
    id: string;
    projectCode: string;
    projectName: string;
    contractId: string;
    projectManager: string;
    projectType: string;
    startDate: string;
    endDate: string;
    totalBudget: number;
    status: number;
    createTime: string;
  }

  /** 立项分页查询参数 */
  export interface InitiationPageQuery {
    pageNum?: number;
    pageSize?: number;
    projectName?: string;
    projectCode?: string;
  }

  /** 立项创建/更新请求参数 */
  export interface InitiationDTO {
    projectCode?: string;
    projectName?: string;
    contractId?: string;
    projectManager?: string;
    projectType?: string;
    startDate?: string;
    endDate?: string;
    totalBudget?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getInitiationPageApi(params: InitiationApi.InitiationPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: InitiationApi.InitiationVO[];
  }>(`/api/v1/project/initiation/page`, { params });
}

/** 查询全部列表 */
export function getInitiationListApi() {
  return requestClient.get<InitiationApi.InitiationVO[]>(`/api/v1/project/initiation/list`);
}

/** 根据 ID 查询 */
export function getInitiationByIdApi(id: string) {
  return requestClient.get<InitiationApi.InitiationVO>(`/api/v1/project/initiation/${id}`);
}

/** 创建 */
export function createInitiationApi(data: InitiationApi.InitiationDTO) {
  return requestClient.post<string>(`/api/v1/project/initiation`, data);
}

/** 更新 */
export function updateInitiationApi(data: InitiationApi.InitiationDTO) {
  return requestClient.put<boolean>(`/api/v1/project/initiation`, data);
}

/** 删除 */
export function deleteInitiationApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/initiation/${id}`);
}
