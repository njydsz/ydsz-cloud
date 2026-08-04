/**
 * Proxy 沙箱 — 基于 fakeWindow 的数据隔离层
 *
 * **ESM 边界声明（重要）**：
 * 本项目子应用通过 ESM `dynamic import()` 加载，模块代码在全局作用域执行，
 * ESM 严格模式禁止 `with` 语句，因此无法像 qiankun/Garfish 那样用
 * `with(fakeWindow)` 包裹子应用代码来拦截顶层全局访问。
 *
 * 故 Proxy 沙箱在本项目中仅提供 `fakeWindow` 数据隔离层：
 * - 子应用通过 `mountProps` 注入的 `fakeWindow` 可用于隔离数据写入
 * - **不拦截**子应用模块顶层对 `window`/`globalThis` 的直接读写
 * - 强隔离需求请使用快照沙箱（默认）+ ESLint `no-restricted-globals` 约束
 *
 * **适用场景**：
 * - 子应用在 mount 阶段需要独立的 fakeWindow 存储隔离数据
 * - 不需要拦截模块顶层全局访问的场景
 *
 * **性能权衡**：
 * - Proxy 拦截有一定性能开销（通常在 1-5%）
 * - 建议在同源子应用集群中优先使用快照沙箱，仅在必要时启用 Proxy 沙箱
 *
 * **对标实现**：
 * - qiankun proxySandbox（含 with 执行，UMD 场景）
 * - Garfish proxySandbox（含 with 执行，UMD 场景）
 * - 本项目因 ESM 路线不使用 with 执行，仅保留 fakeWindow 数据隔离
 *
 * @path comm/effects/micro-kernel/src/proxy-sandbox.ts
 * @author ydsz-team
 * @since 3.2.0
 */

/** Proxy 沙箱实例 */
export interface ProxySandboxInstance {
  /** 沙箱的 fakeWindow（子应用代码中访问的 window） */
  fakeWindow: Record<string, unknown>;
  /** 激活沙箱（恢复 fakeWindow 状态） */
  activate: () => void;
  /** 停用沙箱（记录当前状态） */
  deactivate: () => void;
  /** 清理沙箱（释放资源） */
  cleanup: () => void;
}

/**
 * 创建 Proxy 沙箱实例
 *
 * 每个子应用一个独立的 fakeWindow，所有对 window 的读写都通过 Proxy 拦截，
 * 重定向到 fakeWindow，从而实现真正的运行时隔离。
 *
 * @param appName - 子应用名称（用于调试）
 * @returns Proxy 沙箱实例
 */
export function createProxySandbox(appName: string): ProxySandboxInstance {
  // 真实的 window 对象
  const rawWindow = window;

  // 子应用的 fakeWindow（存储所有修改）
  const fakeWindow = Object.create(null) as Record<string, unknown>;

  // 记录哪些属性是被子应用修改的（用于区分只读和可写）
  const modifiedProps = new Set<string>();

  // 不可修改的全局属性（只读）
  const immutableProps = new Set([
    'window',
    'self',
    'globalThis',
    'top',
    'parent',
    'document',
    'location',
    'navigator',
    'history',
    'localStorage',
    'sessionStorage',
    'indexedDB',
    'caches',
    'crypto',
    'performance',
    'console',
  ]);

  // Proxy 拦截器
  const proxyHandler: ProxyHandler<Record<string, unknown>> = {
    get(target, prop) {
      // 特殊属性直接返回真实 window 的值
      if (prop === Symbol.unscopables) {
        return undefined;
      }

      // Symbol.toPrimitive 等特殊符号
      if (typeof prop === 'symbol') {
        return (rawWindow as any)[prop];
      }

      // 优先从 fakeWindow 读取（子应用修改过的）
      if (prop in target) {
        return target[prop];
      }

      // 否则从真实 window 读取
      const value = (rawWindow as any)[prop];

      // 函数需要绑定到真实 window（如 alert、confirm 等）
      if (typeof value === 'function' && !value.prototype?.constructor) {
        return value.bind(rawWindow);
      }

      return value;
    },

    set(target, prop, value) {
      // 不可修改的属性直接忽略（或抛出警告）
      if (immutableProps.has(prop as string)) {
        if (!import.meta.env.PROD) {
          console.warn(
            `[ProxySandbox:${appName}] Attempt to modify immutable property "${String(prop)}" blocked`,
          );
        }
        return true;
      }

      // 记录修改
      target[prop] = value;
      modifiedProps.add(prop as string);

      return true;
    },

    has(target, prop) {
      // in 操作符：先检查 fakeWindow，再检查真实 window
      return prop in target || prop in (rawWindow as any);
    },

    deleteProperty(target, prop) {
      // 只能删除 fakeWindow 中的属性
      if (prop in target) {
        delete target[prop];
        modifiedProps.delete(prop as string);
      }
      return true;
    },

    getOwnPropertyDescriptor(target, prop) {
      // 优先返回 fakeWindow 的描述符
      if (prop in target) {
        return Object.getOwnPropertyDescriptor(target, prop);
      }

      // 否则返回真实 window 的描述符
      return Object.getOwnPropertyDescriptor(rawWindow, prop);
    },

    defineProperty(target, prop, descriptor) {
      // defineProperty 只作用于 fakeWindow
      Object.defineProperty(target, prop, descriptor);
      modifiedProps.add(prop as string);
      return true;
    },

    getPrototypeOf() {
      // 返回真实 window 的原型（保持原型链一致）
      return Object.getPrototypeOf(rawWindow);
    },
  };

  // 创建 Proxy 对象
  const proxy = new Proxy(fakeWindow, proxyHandler);

  // 沙箱状态标记
  let isActive = false;

  return {
    fakeWindow: proxy,

    activate() {
      if (isActive) return;
      isActive = true;

      // 激活时无需特殊操作，Proxy 会自动拦截所有访问
      if (!import.meta.env.PROD) {
        console.debug(`[ProxySandbox:${appName}] Activated`);
      }
    },

    deactivate() {
      if (!isActive) return;
      isActive = false;

      // 停用时可以记录当前状态（用于调试）
      if (!import.meta.env.PROD) {
        console.debug(
          `[ProxySandbox:${appName}] Deactivated, modified props:`,
          Array.from(modifiedProps),
        );
      }
    },

    cleanup() {
      // 清理 fakeWindow 中的所有属性
      for (const prop of Object.keys(fakeWindow)) {
        delete fakeWindow[prop];
      }
      modifiedProps.clear();
      isActive = false;

      if (!import.meta.env.PROD) {
        console.debug(`[ProxySandbox:${appName}] Cleaned up`);
      }
    },
  };
}


