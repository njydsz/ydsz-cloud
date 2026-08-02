/**
 * dom 工具函数模块
 *
 * @path comm\@core\base\shared\src\utils\dom.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export interface VisibleDomRect {
  bottom: number;
  height: number;
  left: number;
  right: number;
  top: number;
  width: number;
}

/**
 * 获取元素可见信息
 * @param element
 */
export function getElementVisibleRect(
  element?: HTMLElement | null | undefined,
): VisibleDomRect {
  if (!element) {
    return {
      bottom: 0,
      height: 0,
      left: 0,
      right: 0,
      top: 0,
      width: 0,
    };
  }
  const rect = element.getBoundingClientRect();
  const viewHeight = Math.max(
    document.documentElement.clientHeight,
    window.innerHeight,
  );

  const top = Math.max(rect.top, 0);
  const bottom = Math.min(rect.bottom, viewHeight);

  const viewWidth = Math.max(
    document.documentElement.clientWidth,
    window.innerWidth,
  );

  const left = Math.max(rect.left, 0);
  const right = Math.min(rect.right, viewWidth);

  return {
    bottom,
    height: Math.max(0, bottom - top),
    left,
    right,
    top,
    width: Math.max(0, right - left),
  };
}

/**
 * 测量当前环境下浏览器原生滚动条的占位宽度（像素）。
 *
 * @remarks
 * 用于弹窗/抽屉锁定 body 滚动时补偿 `padding-right`，避免滚动条消失导致页面横向抖动。
 *
 * 实现上会**真实操作 DOM**：临时向 `document.body` 追加一个不可见探测元素、读取尺寸后立即移除。
 * 由此带来两个约束：
 * 1. 必须在浏览器环境调用，SSR / Node 下会因 `document` 未定义而抛错；
 * 2. 读取 `offsetWidth` 会触发同步重排（layout thrashing），**不要放在滚动或 resize 回调里高频调用**，
 *    应在模块初始化时测一次并缓存。
 *
 * @returns 滚动条宽度像素值；在 overlay 滚动条系统（如 macOS 默认、移动端）下正常返回 `0`
 */
export function getScrollbarWidth() {
  const scrollDiv = document.createElement('div');

  scrollDiv.style.visibility = 'hidden';
  scrollDiv.style.overflow = 'scroll';
  scrollDiv.style.position = 'absolute';
  scrollDiv.style.top = '-9999px';

  document.body.append(scrollDiv);

  const innerDiv = document.createElement('div');
  scrollDiv.append(innerDiv);

  const scrollbarWidth = scrollDiv.offsetWidth - innerDiv.offsetWidth;

  scrollDiv.remove();
  return scrollbarWidth;
}

/**
 * 判断页面当前是否真的出现了纵向滚动条。
 *
 * @remarks
 * 与 {@link getScrollbarWidth} 配合使用：后者算「滚动条多宽」，本函数判断「现在到底有没有」，
 * 只有两者都成立才需要给 body 补偿 padding。
 *
 * 注意实现中读取 `overflowY` 的分支与兜底分支返回的是同一个表达式，
 * 即当前逻辑等价于「文档滚动高度是否超过视口高度」；保留该分支是为了后续针对
 * `overflow-y: hidden` 等场景做差异化处理时有落点，行为上目前无区别。
 *
 * 该函数会读取 `scrollHeight` 与计算样式，同样会触发重排，避免高频调用。
 *
 * @returns 存在纵向滚动时返回 `true`
 */
export function needsScrollbar() {
  const doc = document.documentElement;
  const body = document.body;

  // 检查 body 的 overflow-y 样式
  const overflowY = window.getComputedStyle(body).overflowY;

  // 如果明确设置了需要滚动条的样式
  if (overflowY === 'scroll' || overflowY === 'auto') {
    return doc.scrollHeight > window.innerHeight;
  }

  // 在其他情况下，根据 scrollHeight 和 innerHeight 比较判断
  return doc.scrollHeight > window.innerHeight;
}

/**
 * 主动派发一次 window `resize` 事件，强制依赖该事件的组件重新计算布局。
 *
 * @remarks
 * 用于「容器尺寸变了但窗口没变」的场景，例如侧边栏折叠、标签页切换后，
 * ECharts、表格等自行监听 resize 的第三方组件不会自动感知，需要手动"踢"一下。
 *
 * 这是**全局副作用**：所有 resize 监听器都会被同步触发，代价不低。
 * 因此调用前建议等待 DOM 过渡动画结束（如 `nextTick` 或 transitionend），
 * 否则组件量到的仍是动画中间态尺寸；也不要在动画每一帧里循环调用。
 *
 * 派发的是原生 `Event` 而非 `UIEvent`，事件对象上没有窗口尺寸相关字段，
 * 监听方若读取 `event.target.innerWidth` 之外的属性需注意兼容。
 */
export function triggerWindowResize(): void {
  // 创建一个新的 resize 事件
  const resizeEvent = new Event('resize');

  // 触发 window 的 resize 事件
  window.dispatchEvent(resizeEvent);
}
