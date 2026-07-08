/**
 * @file DAG 编排引擎 API
 * @description DAG 定义 CRUD、执行、历史查询、实例详情等接口；
 *              对应后端 DagController (/agent/dag)。
 * @module api/agent/dag
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type {
  DagDefinition,
  DagDefinitionDO,
  DagExecutionResult,
  DagInstanceDO,
  DagNodeInstanceDO,
} from './types'

/** 创建 DAG 定义 */
export const createDefinition = (dag: DagDefinition) =>
  request<DagDefinitionDO>({
    url: '/agent/dag',
    method: 'POST',
    data: dag,
  })

/** 查询 DAG 定义详情 */
export const getDefinition = (id: string) =>
  request<DagDefinitionDO>({
    url: `/agent/dag/${id}`,
    method: 'GET',
  })

/** 分页查询 DAG 定义 */
export const pageDefinitions = (page: number, size: number, tenantId?: string) =>
  request<PageResult<DagDefinitionDO>>({
    url: '/agent/dag/page',
    method: 'GET',
    params: { page, size, tenantId },
  })

/** 执行 DAG */
export const executeDag = (definitionId: string, inputs?: Record<string, unknown>) =>
  request<DagExecutionResult>({
    url: `/agent/dag/${definitionId}/execute`,
    method: 'POST',
    data: { inputs },
  })

/** 查询 DAG 执行历史 */
export const pageInstances = (definitionId: string, page: number, size: number) =>
  request<PageResult<DagInstanceDO>>({
    url: `/agent/dag/${definitionId}/instances`,
    method: 'GET',
    params: { page, size },
  })

/** 查询 DAG 实例详情 */
export const getInstance = (instanceId: string) =>
  request<DagInstanceDO>({
    url: `/agent/dag/instance/${instanceId}`,
    method: 'GET',
  })

/** 查询节点执行明细 */
export const listNodeInstances = (instanceId: string) =>
  request<DagNodeInstanceDO[]>({
    url: `/agent/dag/instance/${instanceId}/nodes`,
    method: 'GET',
  })
