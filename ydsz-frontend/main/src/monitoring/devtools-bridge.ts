/**
 * DevTools Bridge —— 将 micro-kernel 生命周期事件桥接到 Chrome Extension
 *
 * 架构：
 *   micro-kernel (scheduler/lifecycle)
 *        ↓  addLifecycleHook callbacks
 *   DevTools Bridge (本模块)
 *        ↓  window.__sendToExtension / window.postMessage
 *   Chrome Extension Content Script
 *        ↓  chrome.runtime.sendMessage
 *   Background Service Worker → DevTools Panel
 *
 * 启用条件：
 *   - import.meta.env.DEV（开发态默认启用）
 *   - localStorage.getItem('micro-kernel:devtools') === '1'（生产态手动开启）
 *
 * @path main/src/monitoring/devtools-bridge.ts
 * @since 4.0.0
 */

import { createLogger } from '@ydsz-core/shared/utils';
import type { MicroAppConfig } from '@ydsz/micro-runtime';

const logger = createLogger('DevToolsBridge');

export interface DevToolsBridgeOptions {
  kernel: any;
  microRuntime?: any;
  forceEnable?: boolean;
  heartbeatMs?: number;
}

type DiagType =
  | 'kernel:state:full'
  | 'lifecycle:beforeLoad' | 'lifecycle:afterLoad'
  | 'lifecycle:beforeMount' | 'lifecycle:afterMount'
  | 'lifecycle:beforeUnmount' | 'lifecycle:afterUnmount'
  | 'lifecycle:error'
  | 'kernel:memory';

interface KernelStateSnapshot {
  appName: string;
  status: string;
  entry: string;
  sandboxType: string;
  loadDuration?: number;
  keepAlive: boolean;
  pinned: boolean;
}

let bridgeActive = false;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;

function shouldEnable(): boolean {
  if (import.meta.env.DEV) return true;
  try { return localStorage.getItem('micro-kernel:devtools') === '1'; } catch { return false; }
}

function sendToExtension(type: DiagType, payload: any): void {
  try {
    const sender = (window as any).__sendToExtension;
    if (typeof sender === 'function') { sender(type, payload); return; }
    window.postMessage({
      channel: '__YDSZ_MICRO_KERNEL__CHANNEL',
      source: 'page',
      type,
      payload,
      _t: Date.now(),
    }, '*');
  } catch { /* 静默失败 */ }
}

function collectFullState(opts: DevToolsBridgeOptions) {
  const kernel = opts.kernel;
  const apps: KernelStateSnapshot[] = [];
  let activeApp: string | null = null;
  let keepAliveCount = 0;

  try {
    const instances = kernel?.getAllInstances?.() ?? new Map();
    for (const [name, inst] of instances) {
      const snap: KernelStateSnapshot = {
        appName: name, status: inst?.status || 'NOT_LOADED', entry: inst?.config?.entry || '',
        sandboxType: inst?.sandboxType || 'snapshot', loadDuration: inst?.loadDuration,
        keepAlive: !!inst?.keepAlive, pinned: !!inst?.pinned,
      };
      apps.push(snap);
      if (snap.keepAlive) keepAliveCount++;
      if (snap.status === 'MOUNTED') activeApp = activeApp || name;
    }
  } catch (err) { logger.warn('收集状态快照失败', err); }

  try {
    const registeredApps = kernel?.getRegisteredApps?.() ?? [];
    for (const reg of registeredApps) {
      if (!apps.find((a) => a.appName === reg.name)) {
        apps.push({
          appName: reg.name, status: 'NOT_LOADED', entry: reg.entry || '',
          sandboxType: (reg as any).sandboxType || 'snapshot', keepAlive: false, pinned: false,
        });
      }
    }
  } catch { /* noop */ }

  let memory = 'N/A';
  try {
    const perf = performance as any;
    if (perf.memory) memory = `${Math.round(perf.memory.usedJSHeapSize / 1024 / 1024)}MB`;
  } catch { /* noop */ }

  return {
    apps, activeApp, keepAliveCount, totalApps: apps.length, memory,
    capabilities: { kernelVersion: kernel?.healthCheck?.()?.kernelVersion || '4.0.0', kernelName: 'micro-kernel' },
  };
}

export function enableDevToolsBridge(opts: DevToolsBridgeOptions): void {
  if (bridgeActive) return;
  if (!opts.forceEnable && !shouldEnable()) { logger.info('DevTools Bridge 未启用'); return; }

  logger.info('DevTools Bridge 启用中...');
  (window as any).__MICRO_KERNEL_DEVTOOLS_ENABLED__ = true;

  const kernel = opts.kernel;
  try {
    kernel?.addLifecycleHook?.('beforeLoad', (app: MicroAppConfig) => {
      sendToExtension('lifecycle:beforeLoad', { appName: app.name, ts: Date.now() });
    });
    kernel?.addLifecycleHook?.('afterLoad', (app: MicroAppConfig) => {
      sendToExtension('lifecycle:afterLoad', { appName: app.name, ts: Date.now() });
    });
    kernel?.addLifecycleHook?.('beforeMount', (app: MicroAppConfig) => {
      sendToExtension('lifecycle:beforeMount', { appName: app.name, ts: Date.now() });
    });
    kernel?.addLifecycleHook?.('afterMount', (app: MicroAppConfig) => {
      sendToExtension('lifecycle:afterMount', { appName: app.name, ts: Date.now() });
      sendToExtension('kernel:state:full', collectFullState(opts));
    });
    kernel?.addLifecycleHook?.('beforeUnmount', (app: MicroAppConfig) => {
      sendToExtension('lifecycle:beforeUnmount', { appName: app.name, ts: Date.now() });
    });
    kernel?.addLifecycleHook?.('afterUnmount', (app: MicroAppConfig) => {
      sendToExtension('lifecycle:afterUnmount', { appName: app.name, ts: Date.now() });
      sendToExtension('kernel:state:full', collectFullState(opts));
    });
    kernel?.addLifecycleHook?.('error', (app: MicroAppConfig, err: any) => {
      sendToExtension('lifecycle:error', { appName: app.name, error: err?.message || String(err), ts: Date.now() });
    });
  } catch (err) { logger.warn('挂载生命周期钩子失败', err); }

  const interval = opts.heartbeatMs ?? 5000;
  heartbeatTimer = setInterval(() => {
    try {
      sendToExtension('kernel:state:full', collectFullState(opts));
      const perf = performance as any;
      if (perf.memory) {
        sendToExtension('kernel:memory', { usedMB: Math.round(perf.memory.usedJSHeapSize / 1024 / 1024), totalMB: Math.round(perf.memory.jsHeapSizeLimit / 1024 / 1024) });
      }
    } catch { /* noop */ }
  }, interval);

  // 监听 Extension 通过 content-script 转发来的命令
  window.addEventListener('message', (e) => {
    if (e.source !== window || !e.data) return;
    if (e.data.channel !== '__YDSZ_MICRO_KERNEL__CHANNEL') return;
    if (e.data.source !== 'extension') return;
    handleExtensionCommand(e.data, opts);
  });

  sendToExtension('kernel:state:full', collectFullState(opts));
  bridgeActive = true;
  logger.info(`DevTools Bridge 已启用（心跳 ${interval}ms）`);
}

function handleExtensionCommand(msg: any, opts: DevToolsBridgeOptions): void {
  const { type, payload } = msg;
  const kernel = opts.kernel;
  switch (type) {
    case 'kernel:state:request':
      sendToExtension('kernel:state:full', collectFullState(opts)); break;
    case 'kernel:health:request': {
      let health: any = { error: 'kernel 不支持 healthCheck' };
      try { health = kernel?.healthCheck?.() ?? health; } catch (e: any) { health = { error: e.message }; }
      sendToExtension('kernel:health:response', health); break;
    }
    case 'kernel:unmount': {
      const name = payload?.appName;
      if (name && typeof kernel?.unmountApp === 'function') {
        kernel.unmountApp(name)
          .then(() => sendToExtension('kernel:state:full', collectFullState(opts)))
          .catch((err: any) => sendToExtension('lifecycle:error', { appName: name, error: err?.message }));
      }
      break;
    }
    case 'kernel:reload': {
      const appName = payload?.appName;
      if (appName && typeof kernel?.loadApp === 'function') {
        kernel.loadApp({ name: appName })
          .then(() => sendToExtension('kernel:state:full', collectFullState(opts)))
          .catch((err: any) => sendToExtension('lifecycle:error', { appName, error: err?.message }));
      }
      break;
    }
    case 'kernel:clear-cache': {
      try {
        if (typeof caches !== 'undefined') {
          caches.keys().then((names) => Promise.all(names.map((n) => caches.delete(n))));
        }
        sendToExtension('kernel:event', { eventName: 'cache-cleared', ts: Date.now() });
      } catch (err: any) { sendToExtension('lifecycle:error', { appName: '*', error: err.message }); }
      break;
    }
    case 'kernel:refresh-registry': {
      try {
        kernel?.refreshRegistry?.();
        sendToExtension('kernel:state:full', collectFullState(opts));
      } catch (err: any) { sendToExtension('lifecycle:error', { appName: '*', error: err.message }); }
      break;
    }
    default: logger.warn(`[DevTools] 未知命令: ${type}`);
  }
}

export function disableDevToolsBridge(): void {
  if (!bridgeActive) return;
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null; }
  (window as any).__MICRO_KERNEL_DEVTOOLS_ENABLED__ = false;
  bridgeActive = false;
}

export function isDevToolsBridgeActive(): boolean { return bridgeActive; }
