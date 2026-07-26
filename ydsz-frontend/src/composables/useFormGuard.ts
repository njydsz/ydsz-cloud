/**
 * @file 表单防误关闭守卫
 * @description 提供表单修改后的离开拦截能力，避免用户误关页面/路由切换导致数据丢失。
 * @module composables/useFormGuard
 *
 * 功能:
 *   1. 监听浏览器 beforeunload 事件(刷新/关闭页面时提示)
 *   2. 监听 vue-router onBeforeRouteLeave(切换路由时提示)
 *   3. 通过 dirty ref 控制是否启用守卫(表单未修改时不提示)
 *   4. 提示消息可自定义
 *   5. 组件卸载时自动清理监听
 *
 * 使用方式:
 *   const { setDirty } = useFormGuard({ message: '表单内容未保存,确定离开?' })
 *   // 表单修改时调用 setDirty(true)
 *   // 表单保存成功后调用 setDirty(false)
 *   // 或传入 Ref<boolean> 作为 dirty 状态
 */
import { ref, onMounted, onUnmounted, getCurrentInstance, type Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'

/** 默认提示文案 */
const DEFAULT_MESSAGE = '表单内容未保存，确定离开？'

export interface UseFormGuardOptions {
  /**
   * 外部传入的 dirty 状态 ref。
   * - 传入时直接监听该 ref
   * - 不传时内部创建一个 ref，并通过返回的 setDirty 方法修改
   */
  dirty?: Ref<boolean>
  /** 离开拦截提示文案 */
  message?: string
}

/**
 * 表单防误关闭守卫
 *
 * 必须在组件 setup 中调用（依赖 onBeforeRouteLeave / onMounted / onUnmounted）。
 *
 * @param options 配置项
 * @returns `{ dirty, setDirty }`
 *   - `dirty`: 当前 dirty 状态（无论内部还是外部创建均会返回）
 *   - `setDirty`: 修改 dirty 状态的方法（仅在不传入 dirty 时有意义，传入时也会同步写入外部 ref）
 */
export function useFormGuard(options: UseFormGuardOptions = {}) {
  const { dirty: externalDirty, message = DEFAULT_MESSAGE } = options

  // 内部 dirty ref：外部传入则复用，否则内部创建
  const internalDirty = externalDirty ?? ref(false)

  /** 设置 dirty 状态 */
  function setDirty(value: boolean): void {
    internalDirty.value = value
  }

  // onBeforeRouteLeave 必须在 setup 中同步调用；composable 内部调用即可
  onBeforeRouteLeave(async (_to, _from, next) => {
    if (!internalDirty.value) {
      next()
      return
    }
    try {
      await ElMessageBox.confirm(message, '提示', {
        confirmButtonText: '离开',
        cancelButtonText: '取消',
        type: 'warning',
      })
      next()
    } catch {
      // 用户取消：阻止路由跳转
      next(false)
    }
  })

  /**
   * beforeunload 监听器：刷新/关闭页面时由浏览器原生提示
   * 现代浏览器忽略自定义文案，但仍需设置 returnValue 触发原生提示
   */
  const handleBeforeUnload = (event: BeforeUnloadEvent) => {
    if (!internalDirty.value) return
    // 现代浏览器忽略自定义文案，但仍需设置 returnValue 触发原生提示
    event.preventDefault()
    event.returnValue = message
  }

  // 仅在组件上下文中注册生命周期（测试 / 非组件场景下静默跳过，避免告警）
  if (getCurrentInstance()) {
    onMounted(() => {
      window.addEventListener('beforeunload', handleBeforeUnload)
    })
    onUnmounted(() => {
      window.removeEventListener('beforeunload', handleBeforeUnload)
    })
  }

  return {
    dirty: internalDirty,
    setDirty,
  }
}
