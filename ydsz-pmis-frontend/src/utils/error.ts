/**
 * @file 错误处理工具
 * @description 提供 SysException / HttpException 类型、统一错误处理函数与确认/成功提示工具
 * @module utils/error
 */
import { ElMessage, ElMessageBox } from 'element-plus'
import i18n from '@/locales'
import { logger } from './logger'

/**
 * 业务异常（携带后端业务码）
 *
 * 用于在业务层 catch 中精确区分业务错误与系统错误：
 * ```ts
 * try {
 *   await someApi()
 * } catch (e) {
 *   handleError(e, '用户模块')
 * }
 * ```
 */
export class SysException extends Error {
  /** 后端业务码 */
  readonly code: number
  /** 拦截器是否已弹错提示 */
  readonly handled: boolean

  constructor(message: string, code: number, handled = true) {
    super(message)
    this.name = 'SysException'
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
  if (e instanceof SysException || e instanceof HttpException) {
    return e.handled
  }
  return false
}

/**
 * 根据 HTTP 状态码返回错误消息
 */
function getHttpErrorMessage(status: number): string {
  const t = i18n.global.t
  const messages: Record<number, string> = {
    400: t('request.badRequest'),
    401: t('request.loginExpired'),
    403: t('request.forbidden'),
    404: t('request.notFound'),
    500: t('request.serverError'),
    502: t('request.badGateway'),
    503: t('request.serviceUnavailable'),
  }
  return messages[status] || t('request.networkAbnormal')
}

/**
 * 统一错误处理函数
 * 根据错误类型自动选择合适的提示方式
 */
export function handleError(error: unknown, context?: string): void {
  const t = i18n.global.t

  // 业务异常（已处理的）
  if (error instanceof SysException) {
    if (error.handled) return // 拦截器已处理，不重复提示
    ElMessage.error(error.message || t('common.operationFailed'))
    return
  }

  // HTTP 异常
  if (error instanceof HttpException) {
    if (error.handled) return
    const statusMessage = getHttpErrorMessage(error.status)
    ElMessage.error(statusMessage)
    return
  }

  // 表单验证错误
  if (error instanceof Error && error.name === 'ValidationError') {
    ElMessage.warning(t('common.pleaseCheckForm'))
    return
  }

  // 未知错误
  const message = error instanceof Error ? error.message : t('common.systemError')
  logger.error('[handleError]', error, context ? { context } : undefined)
  ElMessage.error(message)
}

/**
 * 确认对话框（用于删除等危险操作）
 */
export async function confirmAction(
  message: string,
  title?: string,
): Promise<boolean> {
  const t = i18n.global.t
  try {
    await ElMessageBox.confirm(message, title || t('common.confirm'), {
      confirmButtonText: t('common.ok'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}

/**
 * 成功提示
 */
export function showSuccess(message?: string): void {
  const t = i18n.global.t
  ElMessage.success(message || t('common.operationSuccess'))
}
