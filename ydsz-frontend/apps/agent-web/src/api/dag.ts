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
    /** DAG 唯一 ID */
    id: string;
    /** DAG 编排名称 */
    dagName: string;
    /** DAG 编排配置（节点与边的 JSON 字符串）*/
    dagConfig: string;
    /** 描述 */
    description: string;
    /** 启用状态：0 禁用，1 启用 */
    status: number;
    /** 创建时间（ISO 字符串）*/
    createTime: string;
  }

  export interface DagPageQuery {
    /** 页码，从 1 开始 */
    pageNum?: number;
    /** 每页条数 */
    pageSize?: number;
    /** 名称模糊匹配（可选）*/
    dagName?: string;
  }

  export interface DagDTO {
    /** DAG 编排名称 */
    dagName?: string;
    /** DAG 编排配置（节点与边的 JSON 字符串）*/
    dagConfig?: string;
    /** 描述 */
    description?: string;
    /** 启用状态：0 禁用，1 启用 */
    status?: number;
  }
}

/**
 * 分页查询 Agent DAG 编排列表。
 *
 * @param params - 分页与筛选条件（页码、页大小、名称模糊匹配）
 * @returns 分页结果，含 total 与当前页 items 等元信息
 */
export function getDagPageApi(params: DagApi.DagPageQuery) {
  return requestClient.get<{
    total: number;
    current: number;
    size: number;
    items: DagApi.DagVO[];
  }>(`/api/v1/agent/dag/page`, { params });
}

/**
 * 查询全部 DAG 编排列表（不分页）。
 *
 * @returns DAG 视图对象数组
 */
export function getDagListApi() {
  return requestClient.get<DagApi.DagVO[]>(`/api/v1/agent/dag/list`);
}

/**
 * 根据 ID 查询单个 DAG 编排详情。
 *
 * @param id - DAG 唯一 ID
 * @returns DAG 视图对象
 */
export function getDagByIdApi(id: string) {
  return requestClient.get<DagApi.DagVO>(`/api/v1/agent/dag/${id}`);
}

/**
 * 创建 DAG 编排。
 *
 * @param data - 创建参数（名称、配置、描述等）
 * @returns 新建记录的 ID
 */
export function createDagApi(data: DagApi.DagDTO) {
  return requestClient.post<string>(`/api/v1/agent/dag`, data);
}

/**
 * 更新 DAG 编排（按 ID 全量更新）。
 *
 * @param data - 更新参数，需包含待更新记录的 ID
 * @returns 是否更新成功
 */
export function updateDagApi(data: DagApi.DagDTO) {
  return requestClient.put<boolean>(`/api/v1/agent/dag`, data);
}

/**
 * 删除 DAG 编排。
 *
 * @param id - 待删除的 DAG ID
 * @returns 是否删除成功
 */
export function deleteDagApi(id: string) {
  return requestClient.delete<boolean>(`/api/v1/agent/dag/${id}`);
}
