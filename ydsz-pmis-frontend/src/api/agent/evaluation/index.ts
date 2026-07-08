/**
 * @file Agent 评测框架 API
 * @description 对应后端 AgentEvaluationController (/agent/evaluation)
 * @module api/agent/evaluation
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
import { request } from '@/utils/request'
import type { EvaluationReport, EvaluationRunRequest, EvaluatorType } from './types'

/** 执行评测 */
export const runEvaluation = (req: EvaluationRunRequest) =>
  request<EvaluationReport>({
    url: '/agent/evaluation/run',
    method: 'POST',
    data: req,
  })

/** 获取评估器类型列表 */
export const listEvaluators = () =>
  request<EvaluatorType[]>({
    url: '/agent/evaluation/evaluators',
    method: 'GET',
  })
