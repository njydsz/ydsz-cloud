/**
 * Mock handlers 聚合（批次 20 P1-3 补齐）
 *
 * 命名规范: <module>_<action>
 * 路径: 与后端 controller 路径保持一致 (方便切换到真实后端时只改 baseURL)
 */
import type { MockHandler } from './types'

import { authHandlers } from './handlers/auth'
import { dashboardHandlers } from './handlers/dashboard'
import { projectHandlers } from './handlers/project'
import { executionHandlers } from './handlers/execution'
import { financeHandlers } from './handlers/finance'
import { reportHandlers } from './handlers/report'
import { cockpitHandlers } from './handlers/cockpit'
import { resourceHandlers } from './handlers/resource'
import { userHandlers } from './handlers/user'
import { systemHandlers } from './handlers/system'

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
]
