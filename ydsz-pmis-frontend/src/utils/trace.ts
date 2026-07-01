/**
 * @file 链路追踪 ID 生成
 * @description 生成前端 X-Trace-Id，与后端 MDC 串联实现全链路追踪
 * @module utils/trace
 *
 * ID 格式：`{timestamp-base36}-{random-8}`
 *  - timestamp 部分方便按时间排序
 *  - random 部分避免同一毫秒并发请求冲突
 *
 * 用法：在 utils/request 请求拦截器中 `config.headers['X-Trace-Id'] = generateTraceId()`
 */
/**
 * 生成链路追踪 ID
 * @returns 形如 'lxn1a2b3-abc12345' 的字符串
 */
export function generateTraceId(): string {
  const timestamp = Date.now().toString(36)
  const random = Math.random().toString(36).slice(2, 10)
  return `${timestamp}-${random}`
}
