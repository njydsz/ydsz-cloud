/**
 * @file Agent 评测框架类型定义
 * @module api/agent/evaluation
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 评估器类型 */
export interface EvaluatorType {
  code: string
  desc: string
}

/** 评测用例 DTO */
export interface EvaluationCaseDTO {
  id: string
  userInput: string
  expectedOutput: string
  evaluator: string
  passThreshold: number
  tag?: string
}

/** 评测执行请求 */
export interface EvaluationRunRequest {
  agentType: string
  cases: EvaluationCaseDTO[]
  parallelism?: number
}

/** 单个用例评测结果 */
export interface EvaluationResult {
  caseId: string
  userInput: string
  expectedOutput: string
  actualOutput: string
  score: number
  passed: boolean
  elapsedMs: number
  evaluatorType: string
  errorMessage?: string
}

/** 评测报告 */
export interface EvaluationReport {
  results: EvaluationResult[]
  totalCases: number
  passedCases: number
  failedCases: number
  passRate: number
  averageScore: number
  averageElapsedMs: number
  summary: string
}
