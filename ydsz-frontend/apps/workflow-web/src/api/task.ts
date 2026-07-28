/**
 * 流程任务 API 模块（前端）
 *
 * <p>封装流程任务（{@code ydsz_flow_run_task}）的查询接口调用，对应后端
 * {@code /api/v1/workflow/engine/*} 端点。供「工作流 → 我的待办 / 已办」使用。
 *
 * <p><b>核心接口：</b>
 * <ul>
 *   <li>{@link getTaskPageApi} — 分页查询任务</li>
 *   <li>{@link getTaskListApi} — 全量查询任务</li>
 *   <li>{@link getTaskByIdApi} — 查询任务详情</li>
 *   <li>{@link passTaskApi} — 通过任务（审批同意）</li>
 *   <li>{@link rejectTaskApi} — 驳回任务</li>
 *   <li>{@link transferTaskApi} — 转办任务</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace TaskApi {
  /** 任务视图对象 */
  export interface TaskVO {
    id: string;
    taskName: string;
    processInstanceId: string;
    assignee: string;
    createTime: string;
    dueDate: string;
    status: string;
  }

  /** 任务分页查询条件 */
  export interface TaskPageQuery {
    pageNum?: number;
    pageSize?: number;
    taskName?: string;
    assignee?: string;
  }

  /** 任务传输对象（创建/更新） */
  export interface TaskDTO {
    processInstanceId?: string;
    assignee?: string;
  }
}

/** 分页查询任务 */
export function getTaskPageApi(params: TaskApi.TaskPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: TaskApi.TaskVO[];
  }>(`/api/v1/workflow/engine/page`, { params });
}

/** 查询全部任务列表 */
export function getTaskListApi() {
  return requestClient.get<TaskApi.TaskVO[]>(`/api/v1/workflow/engine/list`);
}

/** 根据 ID 查询 */
export function getTaskByIdApi(id: string) {
  return requestClient.get<TaskApi.TaskVO>(`/api/v1/workflow/engine/${id}`);
}

/** 创建 */
export function createTaskApi(data: TaskApi.TaskDTO) {
  return requestClient.post<string>(`/api/v1/workflow/engine`, data);
}

/** 更新 */
export function updateTaskApi(data: TaskApi.TaskDTO) {
  return requestClient.put<boolean>(`/api/v1/workflow/engine`, data);
}

/** 删除 */
export function deleteTaskApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/workflow/engine/${id}`);
}
