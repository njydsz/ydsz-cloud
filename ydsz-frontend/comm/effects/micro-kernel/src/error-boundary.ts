/**
 * 轻内核错误边界
 *
 * - loadApp 失败 → 渲染降级 UI 到容器
 * - mount 抛错 → 自动卸载 + 标记该应用本次会话降级
 *
 * P0-E1 修复：所有动态值经 escapeHtml 转义后再注入 innerHTML，
 * 防止 config.name / config.entry / config.activeRule 含恶意字符导致 XSS。
 *
 * @path comm/effects/micro-kernel/src/error-boundary.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { MicroAppConfig } from '@ydsz/micro-runtime';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器 */
const logger = createLogger('MicroKernel');

/**
 * P0-E1: 转义 HTML 特殊字符，防止 XSS。
 *
 * 将 &, <, >, ", ' 转义为对应的 HTML 实体，
 * 确保动态值安全地注入 innerHTML。
 */
function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * P0-E1: 将应用名净化为合法的 HTML id。
 *
 * HTML id 不允许空格和特殊字符，将非字母数字字符替换为 `-`。
 */
function sanitizeId(appName: string): string {
  return appName.replace(/[^a-zA-Z0-9_-]/g, '-');
}

/**
 * 错误降级 UI 消息配置，支持 i18n。
 *
 * @remarks
 * 默认提供中英文消息，业务方可通过 {@link setErrorFallbackMessages} 注入自定义消息，
 * 或通过 MicroAppConfig 传入自定义消息覆盖全局配置。
 */
export interface ErrorFallbackMessages {
  /** 错误标题 */
  title: string;
  /** 错误描述 */
  description: string;
  /** 剩余重试次数提示 */
  retriesLeft: string;
  /** 重试按钮文案 */
  retry: string;
  /** 返回首页按钮文案 */
  goHome: string;
  /** 技术详情折叠标题 */
  technicalDetails: string;
  /** 技术详情 - 应用名称 */
  appName: string;
  /** 技术详情 - 入口地址 */
  entry: string;
  /** 技术详情 - 激活规则 */
  activeRule: string;
  /** 技术详情 - 重试次数 */
  retryCount: string;
  /** 重新加载中提示 */
  reloading: string;
  /** 三级降级：前往子应用独立部署地址按钮（v3.7 新增） */
  goToSubAppUrl?: string;
}

/** 默认中文消息 */
const zhCNMessages: ErrorFallbackMessages = {
  title: '应用加载失败',
  description: '子应用可能正在发版或网络异常，请稍后重试。',
  retriesLeft: '剩余重试次数：',
  retry: '重试加载',
  goHome: '返回首页',
  technicalDetails: '技术详情',
  appName: '应用名称：',
  entry: '入口地址：',
  activeRule: '激活规则：',
  retryCount: '重试次数：',
  reloading: '重新加载中...',
  goToSubAppUrl: '前往子应用独立页',
};

/** 默认英文消息 */
const enUSMessages: ErrorFallbackMessages = {
  title: 'Failed to Load Application',
  description: 'The sub-application may be deploying or experiencing network issues. Please try again later.',
  retriesLeft: 'Retries left: ',
  retry: 'Retry',
  goHome: 'Go Home',
  technicalDetails: 'Technical Details',
  appName: 'App Name: ',
  entry: 'Entry: ',
  activeRule: 'Active Rule: ',
  retryCount: 'Retry Count: ',
  reloading: 'Reloading...',
  goToSubAppUrl: 'Open Sub-App Page',
};

/** 当前全局消息配置 */
let globalMessages: ErrorFallbackMessages = zhCNMessages;

/**
 * 设置全局错误降级 UI 消息。
 *
 * @param messages - 消息配置对象
 */
export function setErrorFallbackMessages(messages: ErrorFallbackMessages): void {
  globalMessages = messages;
}

/**
 * 根据语言标识获取预置消息。
 *
 * @param locale - 语言标识，如 'zh-CN'、'en-US'
 * @returns 消息配置对象
 */
export function getErrorFallbackMessagesByLocale(locale: string): ErrorFallbackMessages {
  if (locale.startsWith('en')) {
    return enUSMessages;
  }
  return zhCNMessages;
}

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
/** 用户可见的 micro 重试上限：-1 表示不自动重试（直接走占位 UI） */
const MAX_MICRO_RETRIES = 3;
/** 静默自动重试上限：首次失败后静默重试 N 次（不展示 UI，应对 CDN 偶发抖动） */
const MAX_AUTO_RETRIES = 1;

/** 重置指定应用的重试计数 */
export function resetRetryCount(appName: string): void {
  retryCounters.delete(appName);
}

/** v3.7.0: 读取应用重试计数（内部用） */
export function getRetryCount(appName: string): number {
  return retryCounters.get(appName) ?? 0;
}

/** v3.7.0: 设置应用重试计数 */
export function setRetryCount(appName: string, count: number): void {
  retryCounters.set(appName, count);
}

/**
 * 获取自动重试退避延迟（ms）。
 *
 * 第 n 次退避：baseDelay * 2^n + jitter，用于 CDN 偶发故障恢复。
 */
function getAutoRetryDelay(attempt: number): number {
  const base = 500;
  const jitter = Math.random() * 200;
  return base * 2 ** attempt + jitter;
}

/**
 * 三级降级决策：自动静默重试 → 占位 UI（允许手动重试）→ 整页跳转
 *
 * - attempt < MAX_AUTO_RETRIES → 静默自动重试（不展示 UI）
 * - MAX_AUTO_RETRIES <= attempt < MAX_MICRO_RETRIES → 展示占位 UI 允许手动重试
 * - attempt >= MAX_MICRO_RETRIES → 标记降级 + 整页跳转
 *
 * @param appName - 应用名
 * @returns 'auto-retry' | 'show-ui' | 'full-page'
 */
export function decideDegradationLevel(appName: string): 'auto-retry' | 'show-ui' | 'full-page' {
  const count = retryCounters.get(appName) ?? 0;
  if (count < MAX_AUTO_RETRIES) return 'auto-retry';
  if (count < MAX_MICRO_RETRIES) return 'show-ui';
  return 'full-page';
}

/** 获取下次自动重试延迟（仅 auto-retry 级别有意义） */
export function getNextAutoRetryDelay(appName: string): number {
  const count = retryCounters.get(appName) ?? 0;
  return getAutoRetryDelay(count);
}

/**
 * 渲染错误降级 UI。
 *
 * v3.1: onRetry 回调优先于整页刷新。
 * 点击「重试」时：
 *   - 若 onRetry 存在且重试次数 < MAX_MICRO_RETRIES → 调用 onRetry 重新激活子应用
 *   - 否则 → 回退到 window.location.href 整页跳转
 *
 * v3.2: 增强 UI 展示，提供错误详情、重试计数、返回首页等选项
 *
 * v3.3: 支持 i18n，通过 ErrorFallbackMessages 配置消息
 *
 * v3.4: 支持 HTMLElement 容器，与 MicroAppConfig.container 类型对齐
 *
 * P0-E1: 所有动态值（config.name / config.entry / config.activeRule）经 escapeHtml
 *        转义后注入，防止 XSS。元素 id 使用 sanitizeId 净化后的应用名。
 *
 * @param config - 子应用配置
 * @param container - 容器（HTMLElement 或 null）
 * @param onRetry - 微前端级重试回调（清除降级标记 → 重新激活），不传则直接整页跳转
 * @param messages - 可选的自定义消息配置，不传则使用全局配置
 */
export function renderErrorFallback(
  config: MicroAppConfig,
  container: HTMLElement | null,
  onRetry?: () => Promise<void>,
  messages?: ErrorFallbackMessages,
): void {
  const el = container;
  if (!el) return;

  const retryCount = retryCounters.get(config.name) ?? 0;
  const canRetry = onRetry && retryCount < MAX_MICRO_RETRIES;
  const msg = messages ?? globalMessages;

  // P0-E1: 转义所有动态值，防止 XSS
  const escName = escapeHtml(config.name);
  const escEntry = escapeHtml(config.entry);
  const escActiveRule = escapeHtml(
    typeof config.activeRule === 'string' ? config.activeRule : String(config.activeRule),
  );
  const escTitle = escapeHtml(msg.title);
  const escDescription = escapeHtml(msg.description);
  const escRetry = escapeHtml(msg.retry);
  const escGoHome = escapeHtml(msg.goHome);
  const escTechnicalDetails = escapeHtml(msg.technicalDetails);
  const escAppNameLabel = escapeHtml(msg.appName);
  const escEntryLabel = escapeHtml(msg.entry);
  const escActiveRuleLabel = escapeHtml(msg.activeRule);
  const escRetryCountLabel = escapeHtml(msg.retryCount);
  const escReloading = escapeHtml(msg.reloading);
  const escRetriesLeft = escapeHtml(msg.retriesLeft);
  // v3.7.0: 第三级降级按钮文案
  const escGoToSubAppUrl = escapeHtml(msg.goToSubAppUrl || '独立访问');

  // P0-E1: 净化应用名为合法 HTML id
  const safeId = sanitizeId(config.name);
  const retryBtnId = `micro-kernel-retry-${safeId}`;
  const homeBtnId = `micro-kernel-home-${safeId}`;
  // v3.7.0: 第三级降级按钮 id
  const subAppUrlBtnId = `micro-kernel-suburl-${safeId}`;

  el.innerHTML =
    '<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;' +
    'height:100%;padding:40px;font-family:var(--font-sans, system-ui, -apple-system, sans-serif);' +
    'background:var(--el-bg-color, #fff);color:var(--el-text-color-primary, #303133)">' +
    // 错误图标
    '<div style="width:80px;height:80px;margin-bottom:24px;border-radius:50%;' +
    'background:var(--el-color-danger-light-9, #fef0f0);' +
    'display:flex;align-items:center;justify-content:center">' +
    '<svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="var(--el-color-danger, #f56c6c)" stroke-width="2">' +
    '<circle cx="12" cy="12" r="10"/>' +
    '<line x1="12" y1="8" x2="12" y2="12"/>' +
    '<line x1="12" y1="16" x2="12.01" y2="16"/>' +
    '</svg></div>' +
    // 错误标题
    '<h2 style="margin:0 0 8px;font-size:20px;font-weight:600;color:var(--el-text-color-primary, #303133)">' +
    escTitle +
    '</h2>' +
    // 应用名称
    '<p style="margin:0 0 16px;font-size:14px;color:var(--el-text-color-secondary, #909399)">' +
    escName +
    '</p>' +
    // 错误描述
    '<p style="margin:0 0 24px;font-size:14px;color:var(--el-text-color-regular, #606266);' +
    'text-align:center;max-width:400px;line-height:1.6">' +
    escDescription +
    (canRetry ? '<br/>' + escRetriesLeft + (MAX_MICRO_RETRIES - retryCount) : '') +
    '</p>' +
    // 操作按钮组
    '<div style="display:flex;gap:12px;flex-wrap:wrap;justify-content:center">' +
    (canRetry
      ? '<button id="' + retryBtnId + '" ' +
        'style="padding:10px 24px;background:var(--el-color-primary, #409eff);' +
        'color:#fff;border:none;border-radius:6px;cursor:pointer;' +
        'font-size:14px;font-weight:500;transition:all 0.2s">' +
        escRetry +
        '</button>'
      : '') +
    '<button id="' + homeBtnId + '" ' +
    'style="padding:10px 24px;background:var(--el-fill-color, #f5f7fa);' +
    'color:var(--el-text-color-regular, #606266);border:1px solid var(--el-border-color, #dcdfe6);' +
    'border-radius:6px;cursor:pointer;font-size:14px;font-weight:500;transition:all 0.2s">' +
    escGoHome +
    '</button>' +
    // v3.7.0: 第三级降级按钮 — retry 耗尽后展示，允许用户跳转子应用独立地址
    (!canRetry && escEntry
      ? '<button id="' + subAppUrlBtnId + '" ' +
        'style="padding:10px 24px;background:transparent;' +
        'color:var(--el-color-info, #909399);border:1px dashed var(--el-border-color, #dcdfe6);' +
        'border-radius:6px;cursor:pointer;font-size:14px;font-weight:500;transition:all 0.2s">' +
        escGoToSubAppUrl +
        '</button>'
      : '') +
    '</div>' +
    // 技术详情（可折叠）
    '<details style="margin-top:24px;width:100%;max-width:500px">' +
    '<summary style="cursor:pointer;font-size:13px;color:var(--el-text-color-secondary, #909399);' +
    'padding:8px 0;user-select:none">' +
    escTechnicalDetails +
    '</summary>' +
    '<div style="margin-top:8px;padding:12px;background:var(--el-fill-color-light, #fafafa);' +
    'border-radius:6px;font-size:12px;color:var(--el-text-color-regular, #606266);' +
    'font-family:monospace;word-break:break-all">' +
    '<div>' + escAppNameLabel + escName + '</div>' +
    '<div>' + escEntryLabel + escEntry + '</div>' +
    '<div>' + escActiveRuleLabel + escActiveRule + '</div>' +
    '<div>' + escRetryCountLabel + retryCount + '/' + MAX_MICRO_RETRIES + '</div>' +
    '</div></details>' +
    '</div>';

  // 重试按钮事件
  document.getElementById(retryBtnId)?.addEventListener('click', () => {
    if (!onRetry) return;

    retryCounters.set(config.name, retryCount + 1);

    // 微前端级重试：清除降级标记 → 重新激活
    degradedApps.delete(config.name);
    el.innerHTML =
      '<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;' +
      'height:100%;font-family:var(--font-sans, sans-serif)">' +
      '<div style="width:40px;height:40px;border:3px solid var(--el-border-color-lighter, #ebeef5);' +
      'border-top-color:var(--el-color-primary, #409eff);border-radius:50%;' +
      'animation:spin 0.8s linear infinite"></div>' +
      '<p style="margin:16px 0 0;font-size:14px;color:var(--el-text-color-secondary, #909399)">' +
      escReloading +
      '</p>' +
      '<style>@keyframes spin { to { transform: rotate(360deg); } }</style>' +
      '</div>';

    onRetry().catch(() => {
      // 重试失败 → 重新渲染错误 UI（进入更高级别降级）
      renderErrorFallback(config, el, onRetry, messages);
    });
  });

  // 返回首页按钮事件
  document.getElementById(homeBtnId)?.addEventListener('click', () => {
    window.location.href = '/';
  });

  // v3.7.0: 三级降级第三级按钮 — 跳转子应用独立部署地址（full 模式 = 超过 micro 重试上限）
  // 子应用独立地址可直接访问该子应用的静态资源（不经过基座沙箱/路由）
  // 仅在"剩余重试次数不足"时展示，作为兜底方案
  const subAppUrlBtnId = `micro-kernel-suburl-${safeId}`;
  document.getElementById(subAppUrlBtnId)?.addEventListener('click', () => {
    // 子应用独立地址：使用 entry URL（去除末尾 /）
    const subUrl = config.entry.replace(/\/$/, '');
    window.location.href = subUrl || '/';
  });
}
