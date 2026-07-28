/**
 * Agent DAG 编排 API 模块（前端）
 * <p>封装 Agent 任务的 DAG（有向无环图）编排与执行接口，对应后端 {@code /api/v1/agent/dag/*} 端点。
 * <p>支持多步工具调用、条件分支、并行子任务、失败重试等复杂流程编排。
 * <p>供「Agent 编排 → DAG 设计器」使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { requestClient } from '#/api/request';

export namespace DagApi {
  export interface DagVO {
    id: string;
    dagName: string;
    dagConfig: string;
    description: string;
    status: number;
    createTime: string;
  }

  export interface DagPageQuery {
    pageNum?: number;
    pageSize?: number;
    dagName?: string;
  }

  export interface DagDTO {
    dagName?: string;
    dagConfig?: string;
    description?: string;
    status?: number;
  }
}

/** 分页查询 */
export function getDagPageApi(params: DagApi.DagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DagApi.DagVO[];
  }>(`/api/v1/agent/dag/page`, { params });
}

/** 查询全部列表 */
export function getDagListApi() {
  return requestClient.get<DagApi.DagVO[]>(`/api/v1/agent/dag/list`);
}

/** 根据 ID 查询 */
export function getDagByIdApi(id: string) {
  return requestClient.get<DagApi.DagVO>(`/api/v1/agent/dag/${id}`);
}

/** 创建 */
export function createDagApi(data: DagApi.DagDTO) {
  return requestClient.post<string>(`/api/v1/agent/dag`, data);
}

/** 更新 */
export function updateDagApi(data: DagApi.DagDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/dag`, data);
}

/** 删除 */
export function deleteDagApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/dag/${id}`);
}
