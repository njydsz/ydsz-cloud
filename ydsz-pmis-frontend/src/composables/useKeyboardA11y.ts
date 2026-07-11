/**
 * @file 键盘可访问性辅助函数
 * @description 为使用 @click 的非原生交互元素（如 div）提供键盘等价操作。
 *
 * 背景：
 *   WAI-ARIA 规范要求所有可通过鼠标点击的交互元素也必须支持键盘操作。
 *   对于使用 `<div @click="...">` 的场景，需要同时绑定 Enter 和 Space 键事件。
 *   本工具函数统一生成 keydown 处理器，避免每个组件重复编写。
 *
 * 使用方式：
 *   ```vue
 *   <template>
 *     <div
 *       role="button"
 *       tabindex="0"
 *       @click="handleClick"
 *       @keydown="onKeyActivate(handleClick)"
 *     >
 *       点击我
 *     </div>
 *   </template>
 *
 *   <script setup>
 *   import { onKeyActivate } from '@/composables/useKeyboardA11y'
 *   </script>
 *   ```
 *
 * 或使用 vue 自定义指令 v-a11y-click：
 *   ```vue
 *   <div v-a11y-click="handleClick" role="button" tabindex="0">点击我</div>
 *   ```
 */
import type { Directive } from 'vue'

/**
 * 生成 keydown 处理器，在 Enter / Space 键时触发回调。
 *
 * @param handler 点击/激活回调
 * @returns 可直接绑定到 @keydown 的事件处理器
 *
 * @example
 * ```vue
 * <div role="button" tabindex="0" @click="doSomething" @keydown="onKeyActivate(doSomething)">
 *   可点击也可键盘激活
 * </div>
 * ```
 */
export function onKeyActivate(handler: (e: Event) => void): (e: KeyboardEvent) => void {
  return (e: KeyboardEvent) => {
    if (e.key === 'Enter' || e.key === ' ' || e.key === 'Spacebar') {
      e.preventDefault()
      e.stopPropagation()
      handler(e)
    }
  }
}

/**
 * Vue 自定义指令：v-a11y-click
 *
 * 自动为元素绑定 keydown 事件，在 Enter / Space 键时触发传入的回调。
 * 需配合 role="button" 和 tabindex="0" 使用。
 *
 * @example
 * ```vue
 * <div v-a11y-click="handleClick" role="button" tabindex="0">点击我</div>
 * ```
 */
export const vA11yClick: Directive<HTMLElement, (e: Event) => void> = {
  mounted(el, binding) {
    if (typeof binding.value !== 'function') return
    el.__a11yKeyHandler__ = onKeyActivate(binding.value) as (e: KeyboardEvent) => void
    el.addEventListener('keydown', el.__a11yKeyHandler__)
  },
  updated(el, binding) {
    if (typeof binding.value !== 'function') return
    // 移除旧处理器
    if (el.__a11yKeyHandler__) {
      el.removeEventListener('keydown', el.__a11yKeyHandler__)
    }
    el.__a11yKeyHandler__ = onKeyActivate(binding.value) as (e: KeyboardEvent) => void
    el.addEventListener('keydown', el.__a11yKeyHandler__)
  },
  unmounted(el) {
    if (el.__a11yKeyHandler__) {
      el.removeEventListener('keydown', el.__a11yKeyHandler__)
      delete el.__a11yKeyHandler__
    }
  },
}

// 扩展 HTMLElement 类型以存储 keydown 处理器引用
declare module 'vue' {
  interface ComponentCustomProperties {
    vA11yClick: typeof vA11yClick
  }
}

declare global {
  interface HTMLElement {
    __a11yKeyHandler__?: (e: KeyboardEvent) => void
  }
}
