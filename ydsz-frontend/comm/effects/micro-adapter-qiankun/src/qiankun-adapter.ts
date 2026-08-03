/**
 * Qiankun Adapter — 将 qiankun registerMicroApps / initGlobalState 包装为 MicroRuntime 接口。
 *
 * 迁移期：主应用通过 createRuntime({ kernel: 'qiankun' }) 使用此 adapter，
 * 行为与原有 registerQiankun() 完全一致，但不再直接 import qiankun。
 *
 * 灰度切换到 lite-kernel 时：只需改入参 kernel: 'lite'，业务零改动。
 *
 * @path comm/effects/micro-adapter-qiankun/src/qiankun-adapter.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type {
  MicroAppConfig,
  MicroRuntime,
  LifecycleHook,
  StartOptions,
  UnmountResult,
} from '@ydsz/micro-runtime';

import {
  type RawGlobalStateAPI,
  createGlobalStateHandle,
  type GlobalStateHandle,
} from '@ydsz/micro-runtime';

import { initGlobalState, registerMicroApps, start } from 'qiankun';

/** 将 MicroRuntime 格式的配置转为 qiankun RegisterableApp */
function toQiankunApp(config: MicroAppConfig) {
  return {
    name: config.name,
    entry: config.entry,
    container: config.container,
    activeRule: config.activeRule,
    props: config.props,
  };
}

/**
 * 创建 QiankunAdapter 实现
 */
export function createQiankunAdapter(): MicroRuntime {
  const registeredApps: MicroAppConfig[] = [];
  let started = false;
  let globalStateHandle: null | GlobalStateHandle<Record<string, unknown>> = null;
  let rawGlobalState: null | RawGlobalStateAPI<Record<string, unknown>> = null;

  // 生命周期钩子列表
  const lifecycleHooks = new Map<string, LifecycleHook[]>();

  function addLifecycleHook(
    hookName: 'beforeLoad' | 'afterMount' | 'afterUnmount',
    hook: LifecycleHook,
  ): void {
    if (!lifecycleHooks.has(hookName)) {
      lifecycleHooks.set(hookName, []);
    }
    lifecycleHooks.get(hookName)!.push(hook);
  }

  async function runLifecycleHooks(hookName: string, app: MicroAppConfig): Promise<void> {
    const hooks = lifecycleHooks.get(hookName);
    if (hooks) {
      for (const hook of hooks) {
        await hook(app);
      }
    }
  }

  /** 初始化全局状态通信（与现有 global-state.ts 行为一致） */
  function initGlobalStateCommunication() {
    rawGlobalState = initGlobalState({}) as unknown as RawGlobalStateAPI<Record<string, unknown>>;
    return rawGlobalState;
  }

  /**
   * 提供全局状态句柄给子应用。
   * 由 createSubApp 工厂在执行 mount 时调用。
   */
  function wrapGlobalStateProps(config: MicroAppConfig): Record<string, unknown> {
    if (!rawGlobalState) {
      initGlobalStateCommunication();
    }

    globalStateHandle = createGlobalStateHandle<Record<string, unknown>>(
      { initial: {} },
      rawGlobalState!,
    );

    return {
      onGlobalStateChange: rawGlobalState!.onGlobalStateChange,
      setGlobalState: rawGlobalState!.setGlobalState,
      globalState: globalStateHandle,
    };
  }

  return {
    registerApps(apps) {
      registeredApps.push(...apps);
    },

    getRegisteredApps() {
      return [...registeredApps];
    },

    start(options?: StartOptions) {
      if (started) {
        console.warn('[QiankunAdapter] Already started');
        return;
      }

      initGlobalStateCommunication();

      registerMicroApps(
        registeredApps.map((app) => ({
          ...toQiankunApp(app),
          props: {
            ...app.props,
            ...wrapGlobalStateProps(app),
          },
        })),
        {
          beforeLoad: async (qApp) => {
            const app = registeredApps.find((a) => a.name === qApp.name);
            if (app) await runLifecycleHooks('beforeLoad', app);
          },
          afterMount: async (qApp) => {
            const app = registeredApps.find((a) => a.name === qApp.name);
            if (app) await runLifecycleHooks('afterMount', app);
          },
          afterUnmount: async (qApp) => {
            const app = registeredApps.find((a) => a.name === qApp.name);
            if (app) await runLifecycleHooks('afterUnmount', app);
          },
        },
      );

      start({
        sandbox: {
          experimentalStyleIsolation: options?.sandbox?.styleIsolation ?? true,
        },
        prefetch: typeof options?.prefetch === 'boolean' ? options.prefetch : false,
      });

      started = true;
    },

    async unmountApp(_name: string): Promise<UnmountResult> {
      // qiankun 不暴露手动卸载能力，返回不支持
      return { name: _name, success: false, reason: 'qiankun does not support unmountApp' };
    },

    setKeepAlive(_name: string, _keep: boolean) {
      // qiankun 不原生支持保活
    },

    navigateTo(path: string) {
      // 走主应用 router（由主应用在 start 后自行处理路由跳转）
      window.history.pushState(null, '', path);
      window.dispatchEvent(new PopStateEvent('popstate'));
    },

    addLifecycleHook,

    getActiveAppName() {
      // qiankun 不暴露当前活跃应用，返回 null
      return null;
    },
  };
}
