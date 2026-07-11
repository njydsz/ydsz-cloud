/**
 * @file 乐观更新 composable
 * @description P1-3: 为审批/状态流转/删除等操作提供乐观更新能力：
 *   1. 先修改本地状态（UI 立即响应）
 *   2. 再调用后端 API
 *   3. 失败时自动回滚
 *   4. 可选成功后静默刷新（同步服务端最新状态）
 * @module composables/useOptimisticUpdate
 *
 * 用法：
 *   const { optimistic } = useOptimisticUpdate()
 *
 *   // 删除行（乐观移除，失败回滚）
 *   await optimistic({
 *     mutate: () => { list.value = list.value.filter(r => r.id !== row.id) },
 *     rollback: (snapshot) => { list.value = snapshot },
 *     snapshot: () => [...list.value],
 *     api: () => deleteInvoice(row.id),
 *     successMsg: '删除成功',
 *   })
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'

/** 乐观更新参数 */
export interface OptimisticOptions<T = unknown> {
  /** 乐观变更：立即修改本地状态 */
  mutate: () => void
  /** 快照：在变更前保存当前状态，用于回滚 */
  snapshot: () => T
  /** 回滚：API 失败时恢复本地状态 */
  rollback: (snapshot: T) => void
  /** 后端 API 调用 */
  api: () => Promise<unknown>
  /** 成功提示文案（可选，不传则不提示） */
  successMsg?: string
  /** 失败提示文案（可选，默认使用通用错误提示） */
  errorMsg?: string
  /** 成功后回调（可选，如静默刷新列表） */
  onSuccess?: () => void
}

export function useOptimisticUpdate() {
  const { t } = useI18n()
  /** 操作中标志 */
  const loading = ref(false)

  /**
   * 执行乐观更新
   *
   * @param options 乐观更新参数
   * @returns 是否成功
   */
  async function optimistic<T = unknown>(options: OptimisticOptions<T>): Promise<boolean> {
    const { mutate, snapshot, rollback, api, successMsg, errorMsg, onSuccess } = options

    // 1. 保存快照
    const snap = snapshot()

    // 2. 乐观变更
    mutate()

    loading.value = true
    try {
      // 3. 调用后端 API
      await api()

      // 4. 成功
      if (successMsg) ElMessage.success(successMsg)
      onSuccess?.()
      return true
    } catch (e) {
      // 5. 失败回滚
      rollback(snap)
      ElMessage.error(errorMsg || t('common.optimisticUpdateFailed'))
      return false
    } finally {
      loading.value = false
    }
  }

  return { optimistic, loading }
}
