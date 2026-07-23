/**
 * @file 页面水印工具
 * @description
 *   基于 Canvas 的全屏防截图水印，用于敏感业务页面（合同/财务/客户信息等）。
 *
 *   核心能力：
 *     1. Canvas 绘制文字水印，平铺全屏（background-image: url(canvas.toDataURL())）
 *     2. 高分屏适配（devicePixelRatio 缩放，避免模糊）
 *     3. 多行文本支持（\n 分隔）
 *     4. 防删除：MutationObserver 监听 body 子节点变化，水印被移除时自动恢复
 *     5. 防篡改：监听水印元素属性变化（style/class/id），被修改时重建
 *
 *   安全边界：
 *     - 本方案可抵御普通用户通过 devtools 删除水印元素
 *     - 无法抵御专业用户重写 MutationObserver 原型（需后端截图审计兜底）
 *     - 水印 z-index 默认 9999，pointer-events: none，不影响页面交互
 *
 * @module utils/watermark
 * @since 1.6.0
 */

/** 水印配置选项 */
export interface WatermarkOptions {
  /** 水印文本，支持多行（用 \n 分隔） */
  text: string
  /** 字号 px，默认 16 */
  fontSize?: number
  /** 文字颜色，默认 rgba(0,0,0,0.08)（半透明黑） */
  color?: string
  /** 旋转角度，默认 -22 */
  rotate?: number
  /** 水平间距 px，默认 220 */
  gapX?: number
  /** 垂直间距 px，默认 140 */
  gapY?: number
  /** 层级，默认 9999（高于 Element Plus 动态 z-index 范围） */
  zIndex?: number
}

/** 完整水印配置（合并默认值后） */
type ResolvedWatermarkOptions = Required<WatermarkOptions>

/** 水印 DOM 元素 ID */
const WATERMARK_ID = '__ydsz_watermark__'

/** 默认配置 */
const DEFAULT_OPTIONS: ResolvedWatermarkOptions = {
  text: '',
  fontSize: 16,
  color: 'rgba(0, 0, 0, 0.08)',
  rotate: -22,
  gapX: 220,
  gapY: 140,
  zIndex: 9999,
}

/** 当前水印运行时状态 */
interface WatermarkState {
  options: ResolvedWatermarkOptions
  element: HTMLDivElement
  observer: MutationObserver
}

/** 模块级状态，同一时间只允许存在一个水印实例 */
let currentState: WatermarkState | null = null

/** 标记内部操作，避免 MutationObserver 回调引发无限循环 */
let isInternalMutation = false

/**
 * 生成水印 Canvas 并返回 dataURL
 * 高分屏按 devicePixelRatio 放大画布，避免文字模糊
 */
function generateWatermarkImage(options: ResolvedWatermarkOptions): string {
  const { text, fontSize, color, rotate, gapX, gapY } = options
  const lines = text.split('\n')
  const dpr = window.devicePixelRatio || 1

  const canvas = document.createElement('canvas')
  canvas.width = gapX * dpr
  canvas.height = gapY * dpr

  const ctx = canvas.getContext('2d')
  if (!ctx) return ''

  ctx.scale(dpr, dpr)
  // 移动到单元格中心后旋转，使文字以中心为锚点倾斜
  ctx.translate(gapX / 2, gapY / 2)
  ctx.rotate((rotate * Math.PI) / 180)
  ctx.fillStyle = color
  ctx.font = `${fontSize}px -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif`
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'

  // 多行文本垂直居中排列
  const lineHeight = fontSize * 1.2
  const totalHeight = lineHeight * lines.length
  const startY = -totalHeight / 2 + lineHeight / 2
  lines.forEach((line, idx) => {
    ctx.fillText(line, 0, startY + idx * lineHeight)
  })

  return canvas.toDataURL('image/png')
}

/**
 * 创建水印 DOM 元素
 * position: fixed + inset: 0 实现全屏覆盖，pointer-events: none 透传点击
 */
function createWatermarkElement(dataUrl: string, options: ResolvedWatermarkOptions): HTMLDivElement {
  const div = document.createElement('div')
  div.id = WATERMARK_ID
  div.setAttribute('aria-hidden', 'true')
  div.style.cssText = [
    'position: fixed',
    'inset: 0',
    'width: 100%',
    'height: 100%',
    'pointer-events: none',
    `z-index: ${options.zIndex}`,
    `background-image: url(${dataUrl})`,
    'background-repeat: repeat',
    'background-position: 0 0',
    'visibility: visible',
    'display: block',
    'opacity: 1',
  ].join(';')
  return div
}

/**
 * 确保水印元素存在于 body 中
 * 水印被移除或被移动到其他位置时，重新追加到 body 末尾
 */
function ensureWatermark(): void {
  if (!currentState) return
  if (isInternalMutation) return

  const { element, options } = currentState
  isInternalMutation = true

  // 元素不在 body 中（被删除或被移动），重新追加
  if (!document.body.contains(element) || element.parentElement !== document.body) {
    document.body.appendChild(element)
  }

  // 元素属性被篡改（style/display/visibility/opacity），重建元素
  if (
    element.style.display !== 'block' ||
    element.style.visibility !== 'visible' ||
    element.style.opacity !== '1' ||
    element.style.pointerEvents !== 'none' ||
    element.style.zIndex !== String(options.zIndex)
  ) {
    const dataUrl = generateWatermarkImage(options)
    if (dataUrl) {
      element.style.cssText = createWatermarkElement(dataUrl, options).style.cssText
    }
  }

  isInternalMutation = false
}

/**
 * 设置 MutationObserver 监听
 * - 监听 body childList：水印被 remove 时恢复
 * - 监听水印元素 attributes：style 被篡改时恢复
 */
function setupObserver(state: WatermarkState): MutationObserver {
  const observer = new MutationObserver(() => {
    ensureWatermark()
  })
  // 监听 body 子节点变化（水印被删除）
  observer.observe(document.body, { childList: true })
  // 监听水印元素属性变化（style 被篡改）
  observer.observe(state.element, {
    attributes: true,
    attributeFilter: ['style', 'class', 'id'],
  })
  return observer
}

/**
 * 创建/更新水印
 *
 * 如果已存在水印，会先移除再创建（更新场景）
 *
 * @example
 * ```ts
 * createWatermark({
 *   text: `${realName}\n${dayjs().format('YYYY-MM-DD HH:mm')}`,
 * })
 * ```
 */
export function createWatermark(options: WatermarkOptions): void {
  // 先移除已有水印
  removeWatermark()

  if (typeof document === 'undefined') return

  const merged: ResolvedWatermarkOptions = { ...DEFAULT_OPTIONS, ...options }
  if (!merged.text) return

  const dataUrl = generateWatermarkImage(merged)
  if (!dataUrl) return

  const element = createWatermarkElement(dataUrl, merged)
  isInternalMutation = true
  document.body.appendChild(element)
  isInternalMutation = false

  const state: WatermarkState = { options: merged, element, observer: null as never }
  state.observer = setupObserver(state)
  currentState = state
}

/**
 * 移除水印并释放 observer
 *
 * 登出或切换用户时调用，避免上一用户的水印残留
 */
export function removeWatermark(): void {
  if (!currentState) return

  isInternalMutation = true
  currentState.observer.disconnect()
  if (currentState.element.parentNode) {
    currentState.element.parentNode.removeChild(currentState.element)
  }
  currentState = null
  isInternalMutation = false
}

/**
 * 更新水印文本（保留其他配置）
 *
 * 用户信息变化时调用，避免删除重建带来的闪烁
 *
 * @example
 * ```ts
 * updateWatermark({ text: newUserName })
 */
export function updateWatermark(options: Partial<WatermarkOptions>): void {
  if (!currentState) {
    // 无现有水印，直接创建
    if (options.text) createWatermark(options as WatermarkOptions)
    return
  }

  const merged: ResolvedWatermarkOptions = { ...currentState.options, ...options }
  if (!merged.text) {
    removeWatermark()
    return
  }

  // text 变化需要重新生成 canvas，否则只更新样式
  if (options.text !== undefined || options.fontSize !== undefined || options.color !== undefined) {
    const dataUrl = generateWatermarkImage(merged)
    if (dataUrl) {
      isInternalMutation = true
      currentState.element.style.backgroundImage = `url(${dataUrl})`
      isInternalMutation = false
    }
  }

  if (options.zIndex !== undefined) {
    isInternalMutation = true
    currentState.element.style.zIndex = String(options.zIndex)
    isInternalMutation = false
  }

  currentState.options = merged
}

/**
 * 查询当前水印是否处于激活状态
 */
export function isWatermarkActive(): boolean {
  return currentState !== null
}
