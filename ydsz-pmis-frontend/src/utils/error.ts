/**
 * @file 错误处理工具
 * @description 提供 BizException 类型与错误去重标记，避免拦截器与业务层重复弹错
 * @module utils/error
 */

/**
 * 业务异常（携带后端业务码）
 *
 * 用于在业务层 catch 中精确区分业务错误与系统错误：
 * ```ts
 * try {
 *   await someApi()
 * } catch (e) {
 *   if (e instanceof BizException) {
 *     // 业务错误：拦截器已弹错，这里只做 UI 恢复
 *   } else {
 *     // 系统错误：可能未弹错，按需处理
 *   }
 * }
 * ```
 */
export class BizException extends Error {
  /** 后端业务码 */
  readonly code: number
  /** 拦截器是否已弹错提示 */
  readonly handled: boolean

  constructor(message: string, code: number, handled = true) {
    super(message)
    this.name = 'BizException'
    this.code = code
    this.handled = handled
  }
}

/**
 * 网络异常（HTTP 层错误，非业务码错误）
 */
export class HttpException extends Error {
  readonly status: number
  readonly handled: boolean

  constructor(message: string, status: number, handled = true) {
    super(message)
    this.name = 'HttpException'
    this.status = status
    this.handled = handled
  }
}

/**
 * 判断错误是否已被拦截器处理（已弹错提示）。
 *
 * 业务层 catch 中可用此函数避免重复弹错：
 * ```ts
 * catch (e) {
 *   if (!isHandledError(e)) {
 *     ElMessage.error(e?.message || '操作失败')
 *   }
 *   loading.value = false
 * }
 * ```
 */
export function isHandledError(e: unknown): boolean {
  if (e instanceof BizException || e instanceof HttpException) {
    return e.handled
  }
  return false
}
