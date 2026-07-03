/**
 * @file 全局搜索可见性状态
 * @description 跨组件共享全局搜索弹窗的显示状态：
 *   - AppHeader 的搜索按钮触发 open()
 *   - layout 监听 Ctrl/Cmd+K 快捷键触发 open()
 *   - GlobalSearch 组件绑定 visible 并在关闭时调 close()
 * @module composables/useGlobalSearch
 */
import { ref } from 'vue'

/** 全局搜索弹窗是否可见（模块级单例，跨组件共享） */
const visible = ref(false)

/** 打开全局搜索弹窗 */
function open(): void {
  visible.value = true
}

/** 关闭全局搜索弹窗 */
function close(): void {
  visible.value = false
}

/** 全局搜索 composable */
export function useGlobalSearch() {
  return { visible, open, close }
}
