/**
 * @fileoverview v-lazy 图片懒加载指令
 * @description 基于 IntersectionObserver 实现图片进入视口时才加载 src，减少首屏带宽和渲染开销：
 * - 用法: <img v-lazy="'url'" /> 或 <img v-lazy="{ src, placeholder, rootMargin, threshold }" />
 * - 实现: 优先使用原生 loading="lazy"，回退到 IntersectionObserver
 * - 元素卸载时自动 disconnect，避免内存泄漏
 * - 适用于长列表、表格头像、相册等大量图片场景
 * @module directives/lazy
 * @author ydsz-team
 * @since 1.0.0
 */
import type { App, Directive, DirectiveBinding } from 'vue'

/** v-lazy 绑定值类型 */
interface LazyOptions {
  /** 图片真实地址 */
  src: string
  /** 占位图地址（可选） */
  placeholder?: string
  /** IntersectionObserver rootMargin（可选，默认 '50px' 提前 50px 预加载） */
  rootMargin?: string
  /** IntersectionObserver threshold（可选，默认 0.01） */
  threshold?: number
}

type LazyValue = string | LazyOptions

/** 扩展 HTMLImageElement，存储懒加载指令的内部状态 */
interface LazyImageElement extends HTMLImageElement {
  __lazyObserver?: IntersectionObserver
  __lazyLastSrc?: string
}

/** 存储在元素上的 observer 引用 key */
const OBSERVER_KEY = '__lazyObserver'
/** 存储在元素上的上次 src，用于 updated 钩子判断是否需要重新观察 */
const LAST_SRC_KEY = '__lazyLastSrc'

/**
 * 从绑定值解析出标准配置
 */
function parseOptions(value: LazyValue): LazyOptions {
  if (typeof value === 'string') {
    return { src: value }
  }
  return value
}

/**
 * 加载图片：设置 src 属性
 */
function loadImage(el: HTMLImageElement, src: string): void {
  el.src = src
}

/**
 * 创建 IntersectionObserver 监听元素进入视口
 */
function createObserver(
  el: HTMLImageElement,
  options: LazyOptions,
  onIntersect: () => void,
): IntersectionObserver | null {
  // 浏览器不支持 IntersectionObserver 时直接加载
  if (typeof IntersectionObserver === 'undefined') {
    onIntersect()
    return null
  }

  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          onIntersect()
          observer.disconnect()
          break
        }
      }
    },
    {
      rootMargin: options.rootMargin ?? '50px',
      threshold: options.threshold ?? 0.01,
    },
  )
  observer.observe(el)
  return observer
}

/**
 * 应用懒加载到目标 img 元素
 */
function applyLazy(el: HTMLImageElement, binding: DirectiveBinding<LazyValue>): void {
  const options = parseOptions(binding.value)

  // 清理上次的 observer（updated 时 src 变化需重新观察）
  cleanupObserver(el)

  // 非法 src 直接跳过
  if (!options.src) {
    return
  }

  // 设置占位图
  if (options.placeholder) {
    el.src = options.placeholder
  }

  // 原生 loading="lazy" 优先（现代浏览器零 JS 开销）
  if ('loading' in HTMLImageElement.prototype) {
    el.loading = 'lazy'
    loadImage(el, options.src)
    ;(el as LazyImageElement)[LAST_SRC_KEY] = options.src
    return
  }

  // 回退到 IntersectionObserver
  const observer = createObserver(
    el,
    options,
    () => loadImage(el, options.src),
  )
  if (observer) {
    ;(el as LazyImageElement)[OBSERVER_KEY] = observer
  }
  ;(el as LazyImageElement)[LAST_SRC_KEY] = options.src
}

/**
 * 清理元素上的 observer
 */
function cleanupObserver(el: HTMLImageElement): void {
  const observer = (el as LazyImageElement)[OBSERVER_KEY] as IntersectionObserver | undefined
  if (observer) {
    observer.disconnect()
    delete (el as LazyImageElement)[OBSERVER_KEY]
  }
}

const lazyDirective: Directive<HTMLImageElement, LazyValue> = {
  mounted(el: HTMLImageElement, binding: DirectiveBinding<LazyValue>) {
    applyLazy(el, binding)
  },
  updated(el: HTMLImageElement, binding: DirectiveBinding<LazyValue>) {
    // src 未变化时跳过，避免重复加载
    const options = parseOptions(binding.value)
    if ((el as LazyImageElement)[LAST_SRC_KEY] === options.src) return
    applyLazy(el, binding)
  },
  unmounted(el: HTMLImageElement) {
    cleanupObserver(el)
  },
}

/**
 * 注册 v-lazy 指令到 Vue 应用
 * @param app Vue 应用实例
 */
export function setupLazyDirective(app: App): void {
  app.directive('lazy', lazyDirective)
}

export default lazyDirective
