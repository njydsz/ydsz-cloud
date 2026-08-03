/**
 * 流程实例 API 模块（前端）
 * <p>封装流程实例（{@code ydsz_flow_instance}）CRUD 接口，对应后端 {@code /api/v1/workflow/instance/*} 端点。
 * <p>支持流程启动、挂起、终止、转办、抄送、加签、减签。
 * <p>供「工作流 → 我的发起」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace InstanceApi {
  /** 流程实例视图对象 */
  export interface InstanceVO {
    id: string;
    processInstanceId: string;
    templateName: string;
    starter: string;
    currentTask: string;
    currentAssignee: string;
    status: string;
    startTime: string;
    createTime: string;
  }

  /** 流程实例分页查询参数 */
  export interface InstancePageQuery {
    pageNum?: number;
    pageSize?: number;
    processInstanceId?: string;
    status?: string;
  }

  /** 流程启动请求参数 */
  export interface InstanceDTO {
    templateId?: string;
    starter?: string;
  }
}

/** 分页查询 */
export function getInstancePageApi(params: InstanceApi.InstancePageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: InstanceApi.InstanceVO[];
  }>(`/api/v1/workflow/engine/page`, { params });
}

/** 查询全部列表 */
export function getInstanceListApi() {
  return requestClient.get<InstanceApi.InstanceVO[]>(`/api/v1/workflow/engine/list`);
}

/** 根据 ID 查询 */
export function getInstanceByIdApi(id: string) {
  return requestClient.get<InstanceApi.InstanceVO>(`/api/v1/workflow/engine/${id}`);
}

/** 创建 */
export function createInstanceApi(data: InstanceApi.InstanceDTO) {
  return requestClient.post<string>(`/api/v1/workflow/engine`, data);
}

/** 更新 */
export function updateInstanceApi(data: InstanceApi.InstanceDTO) {
  return requestClient.put<boolean>(`/api/v1/workflow/engine`, data);
}

/** 删除 */
export function deleteInstanceApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/workflow/engine/${id}`);
}
