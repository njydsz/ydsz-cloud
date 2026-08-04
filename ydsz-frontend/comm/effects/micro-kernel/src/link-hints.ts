/**
 * 资源预连接与模块预加载提示（preconnect / modulepreload）。
 *
 * C4 性能优化：在子应用 dynamic import 之前，通过 `<link>` 提示浏览器提前
 * 建立连接与拉取模块，减少 ESM import 的瀑布延迟。
 *
 * - `preconnect`：提前完成 DNS + TCP + TLS 握手，适用于跨域子应用资源域
 * - `dns-prefetch`：仅 DNS 解析，作为 preconnect 的降级回退
 * - `modulepreload`：提前 fetch 并解析 ESM 模块及其静态依赖
 *
 * 所有 hint 均做去重（同 href 不重复注入），且标记 `data-micro-kernel-hint`
 * 以便需要时一键清理。
 *
 * @path comm/effects/micro-kernel/src/link-hints.ts
 * @author ydsz-team
 * @since 3.5.0
 */

/** hint 标记属性，便于排查与清理 */
const HINT_ATTR = 'data-micro-kernel-hint';

/** 已注入的 href 集合，避免重复创建 link 元素 */
const injectedHints = new Set<string>();

/** 支持的 link rel 类型 */
type HintRel = 'dns-prefetch' | 'modulepreload' | 'preconnect' | 'preload';

/**
 * 创建并注入 `<link>` 元素到 `<head>`。
 *
 * 若同 href 已注入则跳过（幂等）。
 *
 * @param rel - link rel 属性值
 * @param href - 资源 URL
 * @param options - 额外属性（crossorigin / as）
 */
function injectLinkHint(
  rel: HintRel,
  href: string,
  options?: { crossorigin?: boolean; as?: string },
): void {
  if (injectedHints.has(href)) return;

  // 若页面已存在同 href + rel 的 link，也视为已注入
  const existing = document.querySelector(`link[rel="${rel}"][href="${href}"]`);
  if (existing) {
    injectedHints.add(href);
    return;
  }

  const link = document.createElement('link');
  link.rel = rel;
  link.href = href;
  link.setAttribute(HINT_ATTR, rel);
  if (options?.crossorigin) {
    link.crossOrigin = 'anonymous';
  }
  if (options?.as) {
    link.setAttribute('as', options.as);
  }
  document.head.appendChild(link);
  injectedHints.add(href);
}

/**
 * 为指定 origin 注入 preconnect 提示。
 *
 * 自动从 URL 中提取 origin（协议 + 域名 + 端口）。
 * 同源资源无需 preconnect（浏览器已复用连接）。
 *
 * @param url - 子应用入口或资源 URL
 */
export function injectPreconnect(url: string): void {
  try {
    const parsed = new URL(url, window.location.origin);
    // 同源无需 preconnect
    if (parsed.origin === window.location.origin) return;
    injectLinkHint('preconnect', parsed.origin, { crossorigin: true });
    // dns-prefetch 作为旧浏览器降级
    injectLinkHint('dns-prefetch', parsed.origin);
  } catch {
    // URL 解析失败时静默跳过
  }
}

/**
 * 为指定 ESM 模块 URL 注入 modulepreload 提示。
 *
 * 浏览器收到 modulepreload 后会提前 fetch + parse 该模块及其静态依赖，
 * 使后续 `import()` 近似命中缓存，减少瀑布延迟。
 *
 * @param moduleUrl - ESM 模块的完整 URL
 */
export function injectModulePreload(moduleUrl: string): void {
  // 相对 URL 需解析为绝对 URL，否则 link href 不生效
  const absoluteUrl = new URL(moduleUrl, window.location.origin).href;
  injectLinkHint('modulepreload', absoluteUrl, { crossorigin: true });
}

/**
 * 批量预加载子应用资源（CSS + ESM entry）。
 *
 * 在 `loadApp` 获取 manifest 后、dynamic import 前调用：
 *   1. 对子应用 origin 注入 preconnect
 *   2. 对 CSS 文件注入 `preload as=style` 提示
 *   3. 对 ESM entry 注入 modulepreload 提示
 *
 * @param entry - 子应用入口基路径（manifest 所在域）
 * @param manifest - 子应用 manifest
 */
export function preloadAppAssets(
  entry: string,
  manifest: { entry: string; css: string[] },
): void {
  // 1. preconnect 子应用资源域
  injectPreconnect(entry);
  injectPreconnect(manifest.entry);

  // 2. preload CSS（rel=preload as=style，不阻塞渲染，仅预取）
  for (const cssHref of manifest.css) {
    const absoluteUrl = new URL(cssHref, window.location.origin).href;
    injectLinkHint('preload', absoluteUrl, { as: 'style' });
  }

  // 3. modulepreload ESM entry（浏览器会自动拉取其静态依赖）
  injectModulePreload(manifest.entry);
}

/**
 * P0-P1: 为子应用 manifest.json 注入 preload 提示。
 *
 * 在 `registerApps` 时调用，让浏览器在页面空闲时提前 fetch manifest.json，
 * 后续 `loadApp` 调用时可直接命中 HTTP 缓存，消除 manifest 串行等待。
 *
 * 仅 build 模式有效（dev 模式无 manifest.json 产物）。
 *
 * @param entry - 子应用入口基路径
 * @since 3.6.1
 */
export function preloadManifest(entry: string): void {
  try {
    const manifestUrl = `${entry.replace(/\/$/, '')}/manifest.json`;
    const absoluteUrl = new URL(manifestUrl, window.location.origin).href;
    // preconnect 提前建立连接（跨域时）
    injectPreconnect(entry);
    // preload manifest.json（as=fetch，不阻塞渲染）
    injectLinkHint('preload', absoluteUrl, { crossorigin: true, as: 'fetch' });
  } catch {
    // URL 解析失败时静默跳过
  }
}

/**
 * 清理所有由本模块注入的 link hints。
 *
 * 用于子应用卸载或内核销毁时清理 DOM。
 */
export function clearLinkHints(): void {
  const hints = document.querySelectorAll(`link[${HINT_ATTR}]`);
  for (const hint of hints) {
    hint.remove();
  }
  injectedHints.clear();
}
