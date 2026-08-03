/**
 * 项目执行 API 模块（前端）
 * <p>封装项目执行（{@code ydsz_project_execution}）接口，对应后端 {@code /api/v1/project/execution/*} 端点。
 * <p>记录项目实施工时、里程碑完成情况、风险与问题。
 * <p>供「项目管理 → 执行跟踪」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace ExecutionApi {
  /** 项目执行任务视图对象 */
  export interface ExecutionVO {
    id: string;
    taskName: string;
    projectId: string;
    assignee: string;
    plannedStart: string;
    plannedEnd: string;
    actualStart: string;
    actualEnd: string;
    progress: number;
    status: number;
    createTime: string;
  }

  /** 执行任务分页查询参数 */
  export interface ExecutionPageQuery {
    pageNum?: number;
    pageSize?: number;
    taskName?: string;
    projectId?: string;
  }

  /** 执行任务创建/更新请求参数 */
  export interface ExecutionDTO {
    taskName?: string;
    projectId?: string;
    assignee?: string;
    plannedStart?: string;
    plannedEnd?: string;
    actualStart?: string;
    actualEnd?: string;
    progress?: number;
    status?: number;
  }
}

/** 分页查询 */
export function getExecutionPageApi(params: ExecutionApi.ExecutionPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: ExecutionApi.ExecutionVO[];
  }>(`/api/v1/project/execution/wbs/task/page`, { params });
}

/** 查询全部列表 */
export function getExecutionListApi() {
  return requestClient.get<ExecutionApi.ExecutionVO[]>(`/api/v1/project/execution/wbs/task/list`);
}

/** 根据 ID 查询 */
export function getExecutionByIdApi(id: string) {
  return requestClient.get<ExecutionApi.ExecutionVO>(`/api/v1/project/execution/wbs/task/${id}`);
}

/** 创建 */
export function createExecutionApi(data: ExecutionApi.ExecutionDTO) {
  return requestClient.post<string>(`/api/v1/project/execution/wbs/task`, data);
}

/** 更新 */
export function updateExecutionApi(data: ExecutionApi.ExecutionDTO) {
  return requestClient.put<boolean>(`/api/v1/project/execution/wbs/task`, data);
}

/** 删除 */
export function deleteExecutionApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/project/execution/wbs/task/${id}`);
}
