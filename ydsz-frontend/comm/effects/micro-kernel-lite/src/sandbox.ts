/**
 * 轻量快照沙箱 — 防意外全局污染。
 *
 * 不做 proxy 拦截（性能与同源性的取舍），仅在子应用 mount/unmount 时执行
 * 快照恢复：记录 window 键集合变更 → unmount 恢复；记录事件监听/定时器 →
 * unmount 清理。
 *
 * **边界声明**：不防恶意代码，仅防「意外污染」。
 * 配合 ESLint no-restricted-globals 规则约束子应用不直接写 window，
 * 本沙箱处置规则兜底的漏网之鱼。
 *
 * 对标 Garfish snapshotSandbox 思路，适配同团队同技术栈场景。
 *
 * @path comm/effects/micro-kernel-lite/src/sandbox.ts
 * @author ydsz-team
 * @since 3.0.0
 */

/** 沙箱实例：记录一个子应用在激活期间的全局副作用 */
export interface SandboxInstance {
  /** mount 前的 window 键快照 */
  windowSnapshot: Set<string>;
  /** mount 前的 window 键值快照（只记录已存在的键） */
  valueSnapshot: Map<string, unknown>;
  /** 此应用注册的 window/document 事件监听器 */
  listeners: Array<{
    target: EventTarget;
    type: string;
    listener: EventListenerOrEventListenerObject;
    options?: boolean | AddEventListenerOptions;
  }>;
  /** 此应用创建的定时器 ID */
  timerIds: number[];
}

/** 记录 addEventListener 原始方法的引用，用于恢复 */
let originalAddEventListener: typeof window.addEventListener;
/** 记录 removeEventListener 原始方法的引用，用于恢复 */
let originalRemoveEventListener: typeof window.removeEventListener;
/** 记录 setTimeout 原始方法的引用 */
let originalSetTimeout: typeof window.setTimeout;
/** 记录 setInterval 原始方法的引用 */
let originalSetInterval: typeof window.setInterval;
/** 记录 clearTimeout 原始方法 */
let originalClearTimeout: typeof window.clearTimeout;
/** 记录 clearInterval 原始方法 */
let originalClearInterval: typeof window.clearInterval;
/** 记录 requestAnimationFrame 原始方法 */
let originalRequestAnimationFrame: typeof window.requestAnimationFrame;
/** 记录 cancelAnimationFrame 原始方法 */
let originalCancelAnimationFrame: typeof window.cancelAnimationFrame;

/** 当前哪个沙箱处于激活状态（同时只允许一个） */
let activeSandbox: SandboxInstance | null = null;

/**
 * 进入沙箱：快照当前 window 状态 + 代理副作用 API。
 *
 * 调用时机：子应用 mount 前。
 */
export function enterSandbox(): SandboxInstance {
  const snapshot = new Set(Object.keys(window));
  const valueSnapshot = new Map<string, unknown>();
  for (const key of snapshot) {
    valueSnapshot.set(key, (window as Record<string, unknown>)[key]);
  }

  const sandbox: SandboxInstance = {
    windowSnapshot: snapshot,
    valueSnapshot,
    listeners: [],
    timerIds: [],
  };

  // 仅在第一个沙箱激活时代理全局 API（幂等）
  if (!activeSandbox) {
    proxyGlobals(sandbox);
  }
  activeSandbox = sandbox;

  return sandbox;
}

/**
 * 退出沙箱：移除新增的 window 键、还原被修改的值、清理事件与定时器。
 *
 * 调用时机：子应用 unmount 后。
 */
export function exitSandbox(sandbox: SandboxInstance): void {
  // 1. 清理定时器
  for (const id of sandbox.timerIds) {
    originalClearTimeout(id);
    originalClearInterval(id);
  }

  // 2. 移除事件监听
  for (const { target, type, listener, options } of sandbox.listeners) {
    originalRemoveEventListener.call(target, type, listener, options);
  }

  // 3. 恢复 window
  const currentKeys = new Set(Object.keys(window));
  for (const key of currentKeys) {
    if (!sandbox.windowSnapshot.has(key)) {
      // 子应用新增的全局变量 → 删除
      delete (window as Record<string, unknown>)[key];
    } else {
      // 子应用修改过的 → 还原
      const original = sandbox.valueSnapshot.get(key);
      if ((window as Record<string, unknown>)[key] !== original) {
        (window as Record<string, unknown>)[key] = original;
      }
    }
  }

  // 4. 清除自身
  activeSandbox = null;
  if (activeSandbox === null) {
    restoreGlobals();
  }
}

/**
 * 代理全局副作用 API，记录子应用注册的监听与定时器。
 *
 * 在第一个沙箱进入时执行，避免重复代理。
 */
function proxyGlobals(sandbox: SandboxInstance): void {
  originalAddEventListener = window.addEventListener.bind(window);
  originalRemoveEventListener = window.removeEventListener.bind(window);

  // --- addEventListener 代理 ---
  window.addEventListener = function proxyAddEventListener(
    this: EventTarget,
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions,
  ): void {
    // 只记录绑定在 windwocument 上的监听（组件级监听由 Vue 自行管理）
    if ((this === window || this === document) && activeSandbox === sandbox) {
      sandbox.listeners.push({ target: this, type, listener, options });
    }
    return originalAddEventListener.call(this, type, listener, options);
  } as typeof window.addEventListener;

  // --- removeEventListener 代理 ---
  window.removeEventListener = function proxyRemoveEventListener(
    this: EventTarget,
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | EventListenerOptions,
  ): void {
    if (activeSandbox === sandbox) {
      const idx = sandbox.listeners.findIndex(
        (l) => l.target === this && l.type === type && l.listener === listener,
      );
      if (idx !== -1) sandbox.listeners.splice(idx, 1);
    }
    return originalRemoveEventListener.call(this, type, listener, options);
  } as typeof window.removeEventListener;

  // --- 定时器代理 ---
  originalSetTimeout = window.setTimeout.bind(window);
  originalSetInterval = window.setInterval.bind(window);
  originalClearTimeout = window.clearTimeout.bind(window);
  originalClearInterval = window.clearInterval.bind(window);
  originalRequestAnimationFrame = window.requestAnimationFrame.bind(window);
  originalCancelAnimationFrame = window.cancelAnimationFrame.bind(window);

  window.setTimeout = function proxySetTimeout(
    handler: TimerHandler,
    timeout?: number,
    ...args: unknown[]
  ): number {
    const id = originalSetTimeout(handler, timeout, ...args);
    if (activeSandbox === sandbox) sandbox.timerIds.push(id);
    return id;
  } as typeof window.setTimeout;

  window.setInterval = function proxySetInterval(
    handler: TimerHandler,
    timeout?: number,
    ...args: unknown[]
  ): number {
    const id = originalSetInterval(handler, timeout, ...args);
    if (activeSandbox === sandbox) sandbox.timerIds.push(id);
    return id;
  } as typeof window.setInterval;

  window.clearTimeout = function proxyClearTimeout(id?: number): void {
    if (activeSandbox === sandbox) {
      const idx = sandbox.timerIds.indexOf(id!);
      if (idx !== -1) sandbox.timerIds.splice(idx, 1);
    }
    return originalClearTimeout(id);
  } as typeof window.clearTimeout;

  window.clearInterval = function proxyClearInterval(id?: number): void {
    if (activeSandbox === sandbox) {
      const idx = sandbox.timerIds.indexOf(id!);
      if (idx !== -1) sandbox.timerIds.splice(idx, 1);
    }
    return originalClearInterval(id);
  } as typeof window.clearInterval;

  // --- requestAnimationFrame 代理 ---
  window.requestAnimationFrame = function proxyRAF(cb: FrameRequestCallback): number {
    const id = originalRequestAnimationFrame(cb);
    if (activeSandbox === sandbox) sandbox.timerIds.push(id);
    return id;
  };

  window.cancelAnimationFrame = function proxyCAF(id: number): void {
    if (activeSandbox === sandbox) {
      const idx = sandbox.timerIds.indexOf(id);
      if (idx !== -1) sandbox.timerIds.splice(idx, 1);
    }
    originalCancelAnimationFrame(id);
  };
}

/** 还原所有代理的全局 API 到原始实现 */
function restoreGlobals(): void {
  if (originalAddEventListener) {
    window.addEventListener = originalAddEventListener;
    window.removeEventListener = originalRemoveEventListener;
    window.setTimeout = originalSetTimeout;
    window.setInterval = originalSetInterval;
    window.clearTimeout = originalClearTimeout;
    window.clearInterval = originalClearInterval;
    window.requestAnimationFrame = originalRequestAnimationFrame;
    window.cancelAnimationFrame = originalCancelAnimationFrame;
  }
}
