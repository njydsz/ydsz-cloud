/**
 * 轻内核错误边界
 *
 * - loadApp 失败 → 渲染降级 UI 到容器
 * - mount 抛错 → 自动卸载 + 标记该应用本次会话降级
 *
 * @path comm/effects/micro-kernel/src/error-boundary.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { MicroAppConfig } from '@ydsz/micro-runtime';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器 */
const logger = createLogger('MicroKernel');

/** 本次会话应用降级 set（key = app.name，该应用不再尝试微前端加载，走整页跳转） */
const degradedApps = new Set<string>();

/** 将指定子应用标记为本次会话降级，后续不再尝试微前端加载，直接整页跳转 */
export function markDegraded(appName: string): void {
  degradedApps.add(appName);
  logger.warn(`${appName} degraded to full-page navigation`);
}

/** 判断指定子应用是否已被标记为本会话降级状态 */
export function isDegraded(appName: string): boolean {
  return degradedApps.has(appName);
}

/** 清空本次会话的全部子应用降级标记 */
export function clearDegraded(): void {
  degradedApps.clear();
}

/** 每个应用的微前端重试计数器（达到上限后回退整页跳转） */
const retryCounters = new Map<string, number>();
const MAX_MICRO_RETRIES = 3;

/** 重置指定应用的重试计数 */
export function resetRetryCount(appName: string): void {
  retryCounters.delete(appName);
}

/**
 * 渲染降级错误 UI。
 *
 * v3.1: onRetry 回调优先于整页刷新。
 * 点击「重试」时：
 *   - 若 onRetry 存在且重试次数 < MAX_MICRO_RETRIES → 调用 onRetry 重新激活子应用
 *   - 否则 → 回退到 window.location.href 整页跳转
 *
 * v3.2: 增强 UI 展示，提供错误详情、重试计数、返回首页等选项
 *
 * @param config - 子应用配置
 * @param container - 容器选择器
 * @param onRetry - 微前端级重试回调（清除降级标记 → 重新激活），不传则直接整页跳转
 */
export function renderErrorFallback(
  config: MicroAppConfig,
  container: string,
  onRetry?: () => Promise<void>,
): void {
  const el = document.querySelector(container);
  if (!el) return;

  const retryCount = retryCounters.get(config.name) ?? 0;
  const canRetry = onRetry && retryCount < MAX_MICRO_RETRIES;

  el.innerHTML = `
    <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;
                height:100%;padding:40px;font-family:var(--font-sans, system-ui, -apple-system, sans-serif);
                background:var(--el-bg-color, #fff);color:var(--el-text-color-primary, #303133)">
      <!-- 错误图标 -->
      <div style="width:80px;height:80px;margin-bottom:24px;border-radius:50%;
                  background:var(--el-color-danger-light-9, #fef0f0);
                  display:flex;align-items:center;justify-content:center">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--el-color-danger, #f56c6c)" stroke-width="2">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
      </div>
      
      <!-- 错误标题 -->
      <h2 style="margin:0 0 8px;font-size:20px;font-weight:600;color:var(--el-text-color-primary, #303133)">
        应用加载失败
      </h2>
      
      <!-- 应用名称 -->
      <p style="margin:0 0 16px;font-size:14px;color:var(--el-text-color-secondary, #909399)">
        ${config.name}
      </p>
      
      <!-- 错误描述 -->
      <p style="margin:0 0 24px;font-size:14px;color:var(--el-text-color-regular, #606266);
                text-align:center;max-width:400px;line-height:1.6">
        子应用可能正在发版或网络异常，请稍后重试。
        ${canRetry ? `<br/>剩余重试次数：${MAX_MICRO_RETRIES - retryCount}` : ''}
      </p>
      
      <!-- 操作按钮组 -->
      <div style="display:flex;gap:12px;flex-wrap:wrap;justify-content:center">
        ${canRetry ? `
          <button id="micro-kernel-retry-${config.name}"
                  style="padding:10px 24px;background:var(--el-color-primary, #409eff);
                         color:#fff;border:none;border-radius:6px;cursor:pointer;
                         font-size:14px;font-weight:500;transition:all 0.2s">
            重试加载
          </button>
        ` : ''}
        <button id="micro-kernel-home-${config.name}"
                style="padding:10px 24px;background:var(--el-fill-color, #f5f7fa);
                       color:var(--el-text-color-regular, #606266);border:1px solid var(--el-border-color, #dcdfe6);
                       border-radius:6px;cursor:pointer;font-size:14px;font-weight:500;transition:all 0.2s">
          返回首页
        </button>
      </div>
      
      <!-- 技术详情（可折叠） -->
      <details style="margin-top:24px;width:100%;max-width:500px">
        <summary style="cursor:pointer;font-size:13px;color:var(--el-text-color-secondary, #909399);
                        padding:8px 0;user-select:none">
          技术详情
        </summary>
        <div style="margin-top:8px;padding:12px;background:var(--el-fill-color-light, #fafafa);
                    border-radius:6px;font-size:12px;color:var(--el-text-color-regular, #606266);
                    font-family:monospace;word-break:break-all">
          <div>应用名称：${config.name}</div>
          <div>入口地址：${config.entry}</div>
          <div>激活规则：${config.activeRule}</div>
          <div>重试次数：${retryCount}/${MAX_MICRO_RETRIES}</div>
        </div>
      </details>
    </div>`;

  // 重试按钮事件
  document.getElementById(`micro-kernel-retry-${config.name}`)?.addEventListener('click', () => {
    if (!onRetry) return;

    retryCounters.set(config.name, retryCount + 1);

    // 微前端级重试：清除降级标记 → 重新激活
    degradedApps.delete(config.name);
    el.innerHTML = `
      <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;
                  height:100%;font-family:var(--font-sans, sans-serif)">
        <div style="width:40px;height:40px;border:3px solid var(--el-border-color-lighter, #ebeef5);
                    border-top-color:var(--el-color-primary, #409eff);border-radius:50%;
                    animation:spin 0.8s linear infinite"></div>
        <p style="margin:16px 0 0;font-size:14px;color:var(--el-text-color-secondary, #909399)">
          重新加载中...
        </p>
        <style>@keyframes spin { to { transform: rotate(360deg); } }</style>
      </div>`;
    
    onRetry().catch(() => {
      // 重试失败 → 重新渲染错误 UI
      renderErrorFallback(config, container, onRetry);
    });
  });

  // 返回首页按钮事件
  document.getElementById(`micro-kernel-home-${config.name}`)?.addEventListener('click', () => {
    window.location.href = '/';
  });
}
