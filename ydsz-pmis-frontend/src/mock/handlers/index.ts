/**
 * Mock handlers 聚合（批次 20 P1-3 补齐）
 *
 * 命名规范: <module>_<action>
 * 路径: 与后端 controller 路径保持一致 (方便切换到真实后端时只改 baseURL)
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
