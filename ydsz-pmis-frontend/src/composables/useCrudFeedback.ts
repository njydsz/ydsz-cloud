/**
 * @file CRUD 操作统一反馈 composable
 * @description 封装增删改查操作的标准化用户反馈流程
 * @module composables/useCrudFeedback
 */
import { ref } from 'vue'
import { handleError, showSuccess, confirmAction, isHandledError } from '@/utils/error'
import i18n from '@/locales'

export interface CrudOptions {
  entityName?: string
  successMessage?: string
  confirmMessage?: string
  onSuccess?: () => void | Promise<void>
  onError?: (error: unknown) => void
  skipConfirm?: boolean
  silent?: boolean
}

export interface BatchResult {
  total: number
  success: number
  failed: number
  failures: { item: unknown; error: unknown }[]
}

export function useCrudFeedback() {
  const submitting = ref(false)

  async function executeCreate(
    apiCall: () => Promise<unknown>,
    options: CrudOptions = {},
  ): Promise<boolean> {
    const { entityName, successMessage, onSuccess, onError, silent } = options
    submitting.value = true
    try {
      await apiCall()
      if (!silent) {
        showSuccess(successMessage || i18n.global.t('common.createSuccess', { name: entityName || '' }))
      }
      await onSuccess?.()
      return true
    } catch (error) {
      if (!isHandledError(error)) {
        handleError(error, `创建${entityName || ''}`)
      }
      onError?.(error)
      return false
    } finally {
      submitting.value = false
    }
  }

  async function executeUpdate(
    apiCall: () => Promise<unknown>,
    options: CrudOptions = {},
  ): Promise<boolean> {
    const { entityName, successMessage, onSuccess, onError, silent } = options
    submitting.value = true
    try {
      await apiCall()
      if (!silent) {
        showSuccess(successMessage || i18n.global.t('common.updateSuccess', { name: entityName || '' }))
      }
      await onSuccess?.()
      return true
    } catch (error) {
      if (!isHandledError(error)) {
        handleError(error, `更新${entityName || ''}`)
      }
      onError?.(error)
      return false
    } finally {
      submitting.value = false
    }
  }

  async function executeDelete(
    apiCall: () => Promise<unknown>,
    options: CrudOptions = {},
  ): Promise<boolean> {
    const { entityName, successMessage, confirmMessage, onSuccess, onError, skipConfirm, silent } = options

    if (!skipConfirm) {
      const msg = confirmMessage || i18n.global.t('common.confirmDelete', { name: entityName || '' })
      const confirmed = await confirmAction(msg)
      if (!confirmed) return false
    }

    submitting.value = true
    try {
      await apiCall()
      if (!silent) {
        showSuccess(successMessage || i18n.global.t('common.deleteSuccess', { name: entityName || '' }))
      }
      await onSuccess?.()
      return true
    } catch (error) {
      if (!isHandledError(error)) {
        handleError(error, `删除${entityName || ''}`)
      }
      onError?.(error)
      return false
    } finally {
      submitting.value = false
    }
  }

  async function executeBatch<T>(
    items: T[],
    apiCall: (item: T) => Promise<unknown>,
    options: CrudOptions = {},
  ): Promise<BatchResult> {
    const { entityName, onSuccess, silent } = options
    const result: BatchResult = {
      total: items.length,
      success: 0,
      failed: 0,
      failures: [],
    }

    submitting.value = true
    for (const item of items) {
      try {
        await apiCall(item)
        result.success++
      } catch (error) {
        result.failed++
        result.failures.push({ item, error })
      }
    }
    submitting.value = false

    // 批量操作反馈
    if (!silent) {
      if (result.failed === 0) {
        showSuccess(i18n.global.t('common.batchSuccess', {
          name: entityName || '',
          n: result.success,
        }))
      } else if (result.success === 0) {
        ElMessage.error(i18n.global.t('common.batchAllFailed', { name: entityName || '' }))
      } else {
        ElMessage.warning(i18n.global.t('common.batchPartialSuccess', {
          name: entityName || '',
          success: result.success,
          failed: result.failed,
        }))
      }
    }

    if (result.success > 0) {
      await onSuccess?.()
    }

    return result
  }

  return {
    submitting,
    executeCreate,
    executeUpdate,
    executeDelete,
    executeBatch,
  }
}
