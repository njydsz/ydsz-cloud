/**
 * 沙箱模块 — 支持快照沙箱与 Proxy 沙箱双模式
 *
 * **快照沙箱（默认）**：
 * - 不做 proxy 拦截（性能与同源性的取舍），仅在子应用 mount/unmount 时执行
 *   快照恢复：记录 window 键集合变更 → unmount 恢复；记录事件监听/定时器 →
 *   unmount 清理。
 * - **边界声明**：不防恶意代码，仅防「意外污染」。
 * - 对标 Garfish snapshotSandbox 思路，适配同团队同技术栈场景。
 *
 * **Proxy 沙箱（可选）**：
 * - 通过 Proxy 拦截所有对 window 的读写，实现真正的运行时隔离。
 * - 适用于多子应用同时激活（keep-alive）或需要更强隔离的场景。
 * - 性能开销约 1-5%，某些第三方库可能依赖真实 window 对象。
 *
 * 配合 ESLint no-restricted-globals 规则约束子应用不直接写 window，
 * 本沙箱处置规则兜底的漏网之鱼。
 *
 * @path comm/effects/micro-kernel/src/sandbox.ts
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
  /** mount 前的 document.title 快照（子应用常修改标题，unmount 时需还原） */
  documentTitleSnapshot: string;
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
/** 记录 requestAnimationFrame 原始方法 */
let originalCancelAnimationFrame: typeof window.cancelAnimationFrame;
/** 标记：定时器 API 是否已被代理（proxyGlobals 中按环境能力设置） */
let timersProxied = false;

/**
 * 沙箱栈：支持嵌套进入。栈顶为当前激活沙箱。
 *
 * v3.1 修复：此前用单一 `activeSandbox` 引用，导致
 *   (1) `exitSandbox` 内 `activeSandbox = null; if (activeSandbox === null)` 恒真，
 *       嵌套场景下任何沙箱退出都会过早 `restoreGlobals`；
 *   (2) `proxyGlobals` 闭包捕获首个 sandbox，嵌套时副作用记录到错误的沙箱。
 *
 * 现采用栈式管理：首个 enter 时代理全局 API（代理函数动态读取栈顶），
 * 最后一个 exit 时还原全局 API。
 */
const sandboxStack: SandboxInstance[] = [];

/** 获取当前栈顶沙箱（无激活时返回 null） */
function topSandbox(): SandboxInstance | null {
  return sandboxStack.length > 0 ? sandboxStack[sandboxStack.length - 1] : null;
}

/**
 * 进入沙箱：快照当前 window 状态 + 代理副作用 API。
 *
 * 调用时机：子应用 mount 前。支持嵌套，栈顶为当前生效沙箱。
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
    documentTitleSnapshot: document.title,
  };

  // 仅在栈为空（首个沙箱）时代理全局 API（幂等）
  if (sandboxStack.length === 0) {
    proxyGlobals();
  }
  sandboxStack.push(sandbox);

  return sandbox;
}

/**
 * 退出沙箱：移除新增的 window 键、还原被修改的值、清理事件与定时器。
 *
 * 调用时机：子应用 unmount 后。栈空时还原全局 API。
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

  // 3.1 还原 document.title（子应用常通过 watchEffect 动态修改标题）
  if (document.title !== sandbox.documentTitleSnapshot) {
    document.title = sandbox.documentTitleSnapshot;
  }

  // 4. 从栈中移除指定沙箱（支持非栈顶退出）
  const idx = sandboxStack.lastIndexOf(sandbox);
  if (idx !== -1) {
    sandboxStack.splice(idx, 1);
  }

  // 5. 仅当栈空时还原全局 API（修复此前恒真 bug）
  if (sandboxStack.length === 0) {
    restoreGlobals();
  }
}

/**
 * 代理全局副作用 API，记录子应用注册的监听与定时器。
 *
 * v3.1 修复：代理函数动态读取栈顶沙箱（`topSandbox()`），而非闭包捕获首个 sandbox，
 * 确保嵌套沙箱期间副作用记录到当前激活的沙箱。
 *
 * 在第一个沙箱进入时执行，避免重复代理。
 */
function proxyGlobals(): void {
  originalAddEventListener = window.addEventListener.bind(window);
  originalRemoveEventListener = window.removeEventListener.bind(window);

  // --- addEventListener 代理 ---
  window.addEventListener = function proxyAddEventListener(
    this: EventTarget,
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions,
  ): void {
    // 只记录绑定在 window/document 上的监听（组件级监听由 Vue 自行管理）
    const current = topSandbox();
    if ((this === window || this === document) && current) {
      current.listeners.push({ target: this, type, listener, options });
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
    const current = topSandbox();
    if (current) {
      const idx = current.listeners.findIndex(
        (l) => l.target === this && l.type === type && l.listener === listener,
      );
      if (idx !== -1) current.listeners.splice(idx, 1);
    }
    return originalRemoveEventListener.call(this, type, listener, options);
  } as typeof window.removeEventListener;

  // --- 定时器代理 ---
  // 防御：在 happy-dom 等非标准环境下 window.setTimeout 可能不存在
  if (typeof window.setTimeout === 'function') {
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
      const current = topSandbox();
      if (current) current.timerIds.push(id);
      return id;
    } as typeof window.setTimeout;

    window.setInterval = function proxySetInterval(
      handler: TimerHandler,
      timeout?: number,
      ...args: unknown[]
    ): number {
      const id = originalSetInterval(handler, timeout, ...args);
      const current = topSandbox();
      if (current) current.timerIds.push(id);
      return id;
    } as typeof window.setInterval;

    window.clearTimeout = function proxyClearTimeout(id?: number): void {
      const current = topSandbox();
      if (current) {
        const idx = current.timerIds.indexOf(id!);
        if (idx !== -1) current.timerIds.splice(idx, 1);
      }
      return originalClearTimeout(id);
    } as typeof window.clearTimeout;

    window.clearInterval = function proxyClearInterval(id?: number): void {
      const current = topSandbox();
      if (current) {
        const idx = current.timerIds.indexOf(id!);
        if (idx !== -1) current.timerIds.splice(idx, 1);
      }
      return originalClearInterval(id);
    } as typeof window.clearInterval;

    // --- requestAnimationFrame 代理 ---
    window.requestAnimationFrame = function proxyRAF(cb: FrameRequestCallback): number {
      const id = originalRequestAnimationFrame(cb);
      const current = topSandbox();
      if (current) current.timerIds.push(id);
      return id;
    };

    window.cancelAnimationFrame = function proxyCAF(id: number): void {
      const current = topSandbox();
      if (current) {
        const idx = current.timerIds.indexOf(id);
        if (idx !== -1) current.timerIds.splice(idx, 1);
      }
      originalCancelAnimationFrame(id);
    };

    timersProxied = true;
  }
}

/** 还原所有代理的全局 API 到原始实现 */
function restoreGlobals(): void {
  if (originalAddEventListener) {
    window.addEventListener = originalAddEventListener;
    window.removeEventListener = originalRemoveEventListener;
    // 仅还原实际被代理过的定时器 API（happy-dom 等环境可能跳过代理）
    if (timersProxied) {
      window.setTimeout = originalSetTimeout;
      window.setInterval = originalSetInterval;
      window.clearTimeout = originalClearTimeout;
      window.clearInterval = originalClearInterval;
      window.requestAnimationFrame = originalRequestAnimationFrame;
      window.cancelAnimationFrame = originalCancelAnimationFrame;
      timersProxied = false;
    }
  }
}
