/**
 * 微前端 DevTools 管理面板（开发态）
 *
 * 通过 Alt+Shift+M 切换面板，可视化展示：
 * - 所有子应用注册状态（NOT_LOADED/LOADING/LOADED/MOUNTED/UNMOUNTED）
 * - 当前 keepAlive 缓存数 / 内存占用
 * - globalState 实时快照
 * - 手动操作按钮（强制卸载/重载/降级/刷新注册表）
 *
 * @path comm/effects/micro-kernel/src/devtools-panel.ts
 * @author ydsz-team
 * @since 3.7.0
 */

import { getAllInstances } from './scheduler';
import { getLogger } from '@ydsz-core/shared/utils';
import { refreshRegistry, resolveAppEntry } from './registry-adapter';
import type { AppStatus } from './scheduler';

const logger = getLogger('MicroKernel');

/** 面板 id，用于去重 */
const PANEL_ID = 'micro-kernel-devtools';

/** 面板是否已挂载 */
let panelMounted = false;

/** 面板是否可见 */
let panelVisible = false;

/** 刷新定时器 */
let refreshTimer: ReturnType<typeof setInterval> | null = null;

/** 获取内存使用（Chrome only） */
function getMemoryInfo(): string {
  const perf = (window as unknown as {
    performance?: { memory?: { usedJSHeapSize: number; jsHeapSizeLimit: number } };
  }).performance;
  const mem = perf?.memory;
  if (!mem) return 'N/A（仅 Chrome）';
  return `${(mem.usedJSHeapSize / 1024 / 1024).toFixed(1)} MB / ${(mem.jsHeapSizeLimit / 1024 / 1024).toFixed(0)} MB`;
}

/** 状态颜色 */
function statusColor(status: AppStatus): string {
  switch (status) {
    case 'MOUNTED':
      return '#67c23a';
    case 'LOADING':
      return '#e6a23c';
    case 'LOADED':
      return '#409eff';
    case 'UNMOUNTED':
      return '#909399';
    case 'NOT_LOADED':
    default:
      return '#c0c4cc';
  }
}

/** 渲染面板 HTML */
function renderPanel(): string {
  const instances = getAllInstances();
  const activeApp = instances.find((i) => i.status === 'MOUNTED');
  const keepAliveCount = instances.filter((i) => i.keepAlive && i.status === 'UNMOUNTED' && i.cachedRoot).length;

  return `
    <div id="${PANEL_ID}-content" style="font-family:monospace;font-size:12px;color:#303133">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
        <strong>🛠 Micro Kernel DevTools</strong>
        <span style="color:#909399">${new Date().toLocaleTimeString()}</span>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:12px">
        <div style="padding:6px;background:#f5f7fa;border-radius:4px">
          <div style="color:#909399">活跃应用</div>
          <div style="font-weight:600">${activeApp?.config.name ?? '无'}</div>
        </div>
        <div style="padding:6px;background:#f5f7fa;border-radius:4px">
          <div style="color:#909399">KeepAlive</div>
          <div style="font-weight:600">${keepAliveCount} / ${instances.length}</div>
        </div>
        <div style="padding:6px;background:#f5f7fa;border-radius:4px">
          <div style="color:#909399">JS 内存</div>
          <div style="font-weight:600">${getMemoryInfo()}</div>
        </div>
        <div style="padding:6px;background:#f5f7fa;border-radius:4px">
          <div style="color:#909399">注册表</div>
          <div style="font-weight:600">${instances.length} 应用</div>
        </div>
      </div>
      <div style="margin-bottom:8px">
        <div style="margin-bottom:4px;color:#606266">子应用状态</div>
        ${instances.map((inst) => `
          <div style="display:flex;align-items:center;gap:6px;padding:4px 0;border-bottom:1px solid #ebeef5">
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${statusColor(inst.status)}"></span>
            <span style="flex:1">${inst.config.name}</span>
            <span style="color:${statusColor(inst.status)};font-weight:600">${inst.status}</span>
            ${inst.loadMetrics ? `<span style="color:#909399">${Math.round(inst.loadMetrics.duration)}ms</span>` : ''}
            <button data-act="unmount" data-app="${inst.config.name}" style="padding:2px 6px;font-size:11px;cursor:pointer;background:#f5f7fa;border:1px solid #dcdfe6;border-radius:3px">卸载</button>
            <button data-act="reload" data-app="${inst.config.name}" style="padding:2px 6px;font-size:11px;cursor:pointer;background:#ecf5ff;border:1px solid #c6e2ff;border-radius:3px;color:#409eff">重载</button>
          </div>
        `).join('')}
      </div>
      <div style="display:flex;gap:6px;flex-wrap:wrap">
        <button id="${PANEL_ID}-refresh-registry" style="padding:4px 10px;font-size:11px;cursor:pointer;background:#f0f9eb;border:1px solid #c2e7b0;border-radius:3px;color:#67c23a">刷新注册表</button>
        <button id="${PANEL_ID}-clear-cache" style="padding:4px 10px;font-size:11px;cursor:pointer;background:#fdf6ec;border:1px solid #f5dab1;border-radius:3px;color:#e6a23c">清缓存</button>
        <button id="${PANEL_ID}-close" style="padding:4px 10px;font-size:11px;cursor:pointer;background:#f4f4f5;border:1px solid #d3d4d6;border-radius:3px;color:#909399">收起</button>
      </div>
    </div>
  `;
}

/** 挂载面板 DOM */
function mountPanel(): HTMLDivElement {
  const el = document.createElement('div');
  el.id = PANEL_ID;
  el.style.cssText = `
    position: fixed; right: 16px; bottom: 16px; z-index: 99999;
    width: 380px; max-height: 70vh; overflow-y: auto;
    background: #fff; border: 1px solid #dcdfe6; border-radius: 8px;
    box-shadow: 0 4px 16px rgba(0,0,0,0.12); padding: 16px;
    display: none;
  `;
  document.body.appendChild(el);
  panelMounted = true;
  return el;
}

/** 绑定面板内按钮事件 */
function bindPanelEvents(el: HTMLDivElement): void {
  el.addEventListener('click', (e) => {
    const target = e.target as HTMLElement;
    const action = target.dataset.act;
    const appName = target.dataset.app;

    if (action === 'unmount' && appName) {
      window.dispatchEvent(new CustomEvent('micro-kernel:devtools:unmount', { detail: { appName } }));
    } else if (action === 'reload' && appName) {
      window.dispatchEvent(new CustomEvent('micro-kernel:devtools:reload', { detail: { appName } }));
    }
  });

  el.querySelector(`#${PANEL_ID}-refresh-registry`)?.addEventListener('click', () => {
    void refreshRegistry().then(() => refreshPanel());
  });
  el.querySelector(`#${PANEL_ID}-clear-cache`)?.addEventListener('click', () => {
    window.dispatchEvent(new CustomEvent('micro-kernel:devtools:clear-cache'));
  });
  el.querySelector(`#${PANEL_ID}-close`)?.addEventListener('click', () => {
    toggleDevTools(false);
  });
}

/** 定时刷新面板数据 */
function startAutoRefresh(el: HTMLDivElement): void {
  if (refreshTimer) clearInterval(refreshTimer);
  refreshTimer = setInterval(() => {
    if (panelVisible) refreshPanel(el);
  }, 2000);
}

function refreshPanel(el?: HTMLDivElement): void {
  const target = el ?? document.getElementById(PANEL_ID);
  if (!target) return;
  const content = target.querySelector(`#${PANEL_ID}-content`);
  if (content) {
    content.innerHTML = renderPanel().match(/<div id="[^"]-content"[^>]*>([\s\S]*)<\/div>\s*<\/div>/)?.[1] ?? '';
  }
}

/** 切换面板显示/隐藏 */
function toggleDevTools(forceVisible?: boolean): void {
  let el = document.getElementById(PANEL_ID);

  if (!el) {
    el = mountPanel();
    bindPanelEvents(el);
  }

  panelVisible = forceVisible ?? !panelVisible;
  el.style.display = panelVisible ? 'block' : 'none';

  if (panelVisible) {
    el.innerHTML = renderPanel();
    bindPanelEvents(el);
    startAutoRefresh(el as HTMLDivElement);
  } else {
    if (refreshTimer) {
      clearInterval(refreshTimer);
      refreshTimer = null;
    }
  }
}

/** 全局键盘监听（避免重复注册） */
let keyHandlerRegistered = false;

function registerKeyHandler(): void {
  if (keyHandlerRegistered || typeof document === 'undefined') return;
  keyHandlerRegistered = true;

  document.addEventListener('keydown', (e) => {
    if (e.altKey && e.shiftKey && (e.key === 'M' || e.key === 'm')) {
      e.preventDefault();
      toggleDevTools();
    }
    if (e.key === 'Escape' && panelVisible) {
      toggleDevTools(false);
    }
  });
}

/**
 * 启用 DevTools 面板。
 *
 * 仅在 import.meta.env.DEV 下生效。
 * 热键：Alt+Shift+M 切换面板，Esc 收起。
 *
 * @example
 * // main/src/bootstrap.ts
 * import { enableMicroDevTools } from '@ydsz/micro-kernel';
 * if (import.meta.env.DEV) enableMicroDevTools();
 */
export function enableMicroDevTools(): void {
  if (import.meta.env.PROD) return;
  registerKeyHandler();
  logger.info('DevTools enabled — press Alt+Shift+M to toggle panel');
}

/** 手动触发显示/隐藏（供外部按钮调用） */
export function toggleMicroDevTools(visible?: boolean): void {
  toggleDevTools(visible);
}

/** 销毁面板（测试/HMR场景） */
export function destroyMicroDevTools(): void {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
  document.getElementById(PANEL_ID)?.remove();
  panelMounted = false;
  panelVisible = false;
}
