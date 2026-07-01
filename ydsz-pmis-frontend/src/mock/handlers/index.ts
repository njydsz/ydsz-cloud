/**
 * @file Mock handlers 聚合入口（批次 20 P1-3 补齐）
 * @description 聚合所有模块的 Mock 处理器并统一导出, 供 vite-plugin-mock 注册到 dev server。
 *
 * 命名规范: <module>_<action>
 * 路径: 与后端 controller 路径保持一致 (方便切换到真实后端时只改 baseURL)
 *
 * @module mock/handlers
 */
import type { MockHandler } from './types'

import { authHandlers } from './auth'
import { dashboardHandlers } from './dashboard'
import { projectHandlers } from './project'
import { executionHandlers } from './execution'
import { financeHandlers } from './finance'
import { reportHandlers } from './report'
import { cockpitHandlers } from './cockpit'
import { resourceHandlers } from './resource'
import { userHandlers } from './user'
import { systemHandlers } from './system'
import { agentHandlers } from './agent'
import { chaosHandlers } from './chaos'

/**
 * 全局 Mock 处理器列表
 *
 * 将各业务模块 (auth/project/execution/finance/report 等) 的处理器按顺序展开合并,
 * 由 vite-plugin-mock 在 dev server 中间件里统一注册与匹配。
 *
 * @returns 全部模块 Mock 处理器的合并数组
 */
export const mockHandlers: MockHandler[] = [
  ...authHandlers,
  ...dashboardHandlers,
  ...projectHandlers,
  ...executionHandlers,
  ...financeHandlers,
  ...reportHandlers,
  ...cockpitHandlers,
  ...resourceHandlers,
  ...userHandlers,
  ...systemHandlers,
  ...agentHandlers,
  ...chaosHandlers,
]
