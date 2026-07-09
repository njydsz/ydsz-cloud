/**
 * @file 对话框表单守卫 composable
 * @description 在对话框关闭前检测表单是否有未保存修改，提示用户确认。
 *              补充 useFormGuard 无法覆盖的 el-dialog/el-drawer 场景。
 * @module composables/useDialogGuard
 *
 * 用法：
 *   const { visible, guardClose } = useDialogGuard()
 *   const { dirty, setDirty } = useFormGuard()
 *
 *   // el-dialog 的 before-close 钩子
 *   <el-dialog v-model="visible" :before-close="guardClose(() => dirty.value)">
 *
 *   // 或直接使用
 *   function handleClose() {
 *     guardClose(() => dirty.value)()
 *   }
 */
import { ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import i18n from '@/locales'

export interface UseDialogGuardOptions {
  /** 离开提示文案 */
  message?: string
  /** 确认按钮文案 */
  confirmText?: string
  /** 取消按钮文案 */
  cancelText?: string
}

/**
 * 对话框表单守卫
 *
 * @param options 配置项
 * @returns `{ visible, guardClose }`
 *   - `visible`: 对话框可见性 ref
 *   - `guardClose`: 返回 before-close 处理函数，传入 isDirty 函数判断是否脏
 */
export function useDialogGuard(options: UseDialogGuardOptions = {}) {
  const {
    message = i18n.global.t('common.unsavedChanges') || '表单内容未保存，确定关闭？',
    confirmText = i18n.global.t('common.confirm') || '确定',
    cancelText = i18n.global.t('common.cancel') || '取消',
  } = options

  const visible = ref(false)

  /**
   * 生成 before-close 处理函数
   *
   * @param isDirty 判断表单是否脏的函数（返回 boolean 或 Ref<boolean> 的 value）
   * @returns el-dialog/el-drawer 的 before-close 处理函数
   *
   * 用法：
   *   <el-dialog :before-close="guardClose(() => formDirty.value)">
   */
  function guardClose(isDirty: () => boolean) {
    return async (done: () => void) => {
      if (!isDirty()) {
        done()
        return
      }
      try {
        await ElMessageBox.confirm(message, i18n.global.t('common.tip') || '提示', {
          confirmButtonText: confirmText,
          cancelButtonText: cancelText,
          type: 'warning',
        })
        done()
      } catch {
        // 用户取消，不关闭
      }
    }
  }

  /**
   * 编程式关闭（带守卫检查）
   *
   * @param isDirty 判断表单是否脏的函数
   * @returns 是否成功关闭
   */
  async function safeClose(isDirty: () => boolean): Promise<boolean> {
    if (isDirty()) {
      try {
        await ElMessageBox.confirm(message, i18n.global.t('common.tip') || '提示', {
          confirmButtonText: confirmText,
          cancelButtonText: cancelText,
          type: 'warning',
        })
      } catch {
        return false
      }
    }
    visible.value = false
    return true
  }

  return {
    visible,
    guardClose,
    safeClose,
  }
}
