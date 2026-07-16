/**
 * OpenAPI 3.0 Schema 类型定义
 *
 * @openapi-signature: 1848275a8376936e
 *
 * 说明:
 *   本文件记录后端 API 的签名哈希,用于 CI 漂移检测。
 *   当后端 Controller 的 @*Mapping 注解发生变更时,签名会变化,
 *   CI 会失败并提示运行 `pnpm openapi:gen` 重新生成完整类型。
 *
 *   完整的 OpenAPI 类型定义需要本地启动后端后运行:
 *     pnpm openapi:gen
 *   该命令会从 http://localhost:9000/v3/api-docs 拉取 spec 并生成完整类型,
 *   同时会保留本文件的签名标记。
 *
 * 当前签名对应的 Controller 数: 94, 端点数: 703
 */

/* eslint-disable @typescript-eslint/no-empty-interface */

/**
 * 通用响应封装
 */
export interface CommonResult<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: string
}

/**
 * 分页查询响应
 */
export interface PageResult<T = unknown> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/**
 * 路径占位符(完整类型需运行 pnpm openapi:gen 生成)
 */
export interface paths {
  [path: string]: unknown
}

/**
 * 组件类型占位(完整类型需运行 pnpm openapi:gen 生成)
 */
export interface components {
  schemas: Record<string, unknown>
}
