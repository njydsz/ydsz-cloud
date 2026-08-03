/**
 * 轻内核错误边界
 *
 * - loadApp 失败 → 渲染降级 UI 到容器
 * - mount 抛错 → 自动卸载 + 标记该应用本次会话降级
 *
 * @path comm/effects/micro-kernel-lite/src/error-boundary.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { MicroAppConfig } from '@ydsz/micro-runtime';

/** 本次会话应用降级 set（key = app.name，该应用不再尝试微前端加载，走整页跳转） */
const degradedApps = new Set<string>();

/** 将指定子应用标记为本次会话降级，后续不再尝试微前端加载，直接整页跳转 */
export function markDegraded(appName: string): void {
  degradedApps.add(appName);
  console.warn(`[LiteKernel] ${appName} degraded to full-page navigation`);
}

/** 判断指定子应用是否已被标记为本会话降级状态 */
export function isDegraded(appName: string): boolean {
  return degradedApps.has(appName);
}

/** 清空本次会话的全部子应用降级标记 */
export function clearDegraded(): void {
  degradedApps.clear();
}

/** 渲染降级错误 UI */
export function renderErrorFallback(config: MicroAppConfig, container: string): void {
  const el = document.querySelector(container);
  if (!el) return;

  el.innerHTML = `
    <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;
                height:100%;color:var(--color-text-secondary, #666);font-size:13px;font-family:var(--font-sans, sans-serif)">
      <div style="font-size:48px;margin-bottom:16px;opacity:0.3">!</div>
      <div style="font-weight:500;margin-bottom:8px">${config.name} 加载失败</div>
      <div style="margin-bottom:16px">子应用可能正在发版或网络异常</div>
      <button id="lite-kernel-retry-${config.name}"
              style="padding:6px 16px;background:var(--color-background-primary, #fff);
                     border:0.5px solid var(--color-border-primary, #ccc);
                     border-radius:8px;cursor:pointer;font-size:13px">
        重试
      </button>
    </div>`;

  // 重试按钮：整页刷新
  document.getElementById(`lite-kernel-retry-${config.name}`)?.addEventListener('click', () => {
    window.location.reload();
  });
}
