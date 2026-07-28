/**
 * 任务 DAG 编排 API 模块（前端）
 * <p>封装任务 DAG（{@code ydsz_job_dag}）编排接口，对应后端 {@code /api/v1/cronjob/jobDag/*} 端点。
 * <p>支持多任务依赖编排、串并行执行、失败补偿、人工干预节点。
 * <p>供「任务调度 → DAG 编排」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace JobDagApi {
  export interface JobDagVO {
    id: string;
    dagName: string;
    dagCode: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface JobDagPageQuery {
    pageNum?: number;
    pageSize?: number;
    dagName?: string;
  }

  export interface JobDagDTO {
    dagName?: string;
    dagCode?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getJobDagPageApi(params: JobDagApi.JobDagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: JobDagApi.JobDagVO[];
  }>(`/api/v1/cronjob/dag/page`, { params });
}

/** 查询全部列表 */
export function getJobDagListApi() {
  return requestClient.get<JobDagApi.JobDagVO[]>(`/api/v1/cronjob/dag/list`);
}

/** 根据 ID 查询 */
export function getJobDagByIdApi(id: string) {
  return requestClient.get<JobDagApi.JobDagVO>(`/api/v1/cronjob/dag/${id}`);
}

/** 创建 */
export function createJobDagApi(data: JobDagApi.JobDagDTO) {
  return requestClient.post<string>(`/api/v1/cronjob/dag`, data);
}

/** 更新 */
export function updateJobDagApi(data: JobDagApi.JobDagDTO) {
  return requestClient.put<boolean>(`/api/v1/cronjob/dag`, data);
}

/** 删除 */
export function deleteJobDagApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/cronjob/dag/${id}`);
}
