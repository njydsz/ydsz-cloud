/**
 * Mock 处理器通用类型
 */
export interface MockContext {
  query: Record<string, string>
  body: unknown
}

export type MockMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'

export type MockHandlerFn = (ctx: MockContext) => Promise<unknown> | unknown

export interface MockHandler {
  method: MockMethod
  path: string
  handler: MockHandlerFn
  description?: string
}
