/**
 * 链路追踪 ID 生成
 */
export function generateTraceId(): string {
  const timestamp = Date.now().toString(36)
  const random = Math.random().toString(36).slice(2, 10)
  return `${timestamp}-${random}`
}
