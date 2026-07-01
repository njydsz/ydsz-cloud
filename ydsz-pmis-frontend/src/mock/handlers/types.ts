/**
 * @file Mock 处理器通用类型定义
 * @description 定义 Mock 处理器(handler)及其执行上下文、方法枚举等基础类型,
 *              是所有模块 Mock 处理器共享的契约类型。
 * @module mock/handlers/types
 */

/**
 * Mock 处理器执行上下文
 */
export interface MockContext {
  /** URL query 参数 (已解码, 值统一为 string) */
  query: Record<string, string>
  /** 请求体 (GET/DELETE 为 null, 其余方法为解析后的 JSON 或原始字符串) */
  body: unknown
}

/** Mock 支持的 HTTP 方法枚举 */
export type MockMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'

/**
 * Mock 处理器函数签名
 * @param ctx Mock 执行上下文 (query + body)
 * @returns 同步或异步返回的 Mock 响应数据 (会被插件包装为统一响应结构)
 */
export type MockHandlerFn = (ctx: MockContext) => Promise<unknown> | unknown

/**
 * 单个 Mock 处理器描述对象
 */
export interface MockHandler {
  /** HTTP 方法 */
  method: MockMethod
  /** 匹配路径 (支持 {id} 占位符, 已剥离 /api/v1 前缀) */
  path: string
  /** 处理函数, 返回 Mock 数据 */
  handler: MockHandlerFn
  /** 可选描述, 用于文档/调试 */
  description?: string
}
