/**
 * iframe 沙箱 — 基于 iframe contentWindow 的强隔离兜底方案
 *
 * **设计定位**：
 * 快照沙箱（防意外污染）和 Proxy 沙箱（fakeWindow 数据隔离）均运行在主窗口
 * 同一 realm，无法隔离 CSS 与 DOM 全局选择器。iframe 沙箱通过创建独立的
 * 浏览上下文（browsing context）提供 **CSS + DOM + window** 三重隔离，
 * 作为强隔离需求的兜底方案。
 *
 * **ESM 边界说明**：
 * 与 Proxy 沙箱相同，子应用通过 ESM `dynamic import()` 加载，模块代码在
 * 主 realm 执行而非 iframe 内。因此 iframe 沙箱在本项目中提供：
 * - **CSS 隔离**：将子应用挂载容器移入 iframe document，样式选择器天然隔离
 * - **DOM 隔离**：iframe 有独立 document，querySelector 等不跨域
 * - **fakeWindow**：iframe 的 contentWindow 可作为 mountProps 注入的隔离 window
 *
 * **适用场景**：
 * - 子应用使用全局 CSS 选择器（如 `body { ... }`）可能与主应用冲突时
 * - 需要完全独立的 document 环境的第三方子应用
 * - snapshot/proxy 沙箱隔离不足时的兜底降级
 *
 * **限制**：
 * - iframe 创建有额外开销（首次约 10-30ms）
 * - 跨 realm 通信需通过 postMessage，事件系统不直通
 * - 弹窗/抽屉等 fixed 定位元素会被限制在 iframe 视口内
 *
 * **对标实现**：
 * - wujie（iframe + webcomponent 方案，本项目精简为纯 iframe 容器）
 * - micro-app（webcomponent + iframe scope，本项目仅取 iframe window 隔离）
 *
 * @path comm/effects/micro-kernel/src/iframe-sandbox.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/** iframe 沙箱实例 */
export interface IframeSandboxInstance {
  /** iframe 的 contentWindow（子应用可用的隔离 window） */
  contentWindow: Window | null;
  /** iframe 的 contentDocument（子应用挂载用的隔离 document） */
  contentDocument: Document | null;
  /** 挂载容器元素（位于 iframe document 内） */
  container: HTMLElement | null;
  /** 激活沙箱 */
  activate: () => void;
  /** 停用沙箱 */
  deactivate: () => void;
  /** 清理沙箱（移除 iframe，释放资源） */
  cleanup: () => void;
}

/** iframe 默认样式：撑满容器、无边框 */
const IFRAME_STYLE =
  'width:100%;height:100%;border:0;display:block;margin:0;padding:0;';

/**
 * 创建 iframe 沙箱实例。
 *
 * 在指定的父容器内创建一个隐藏 iframe，iframe 加载空白文档后，
 * 将主应用的基础样式（CSS 变量、reset 等）注入 iframe document，
 * 并在 iframe 内创建一个挂载容器元素供子应用渲染。
 *
 * @param appName - 子应用名称（用于调试与 iframe title 属性）
 * @param parentEl - 父容器元素，iframe 将挂载到此元素内
 * @returns iframe 沙箱实例
 */
export function createIframeSandbox(
  appName: string,
  parentEl: HTMLElement,
): IframeSandboxInstance {
  const iframe = document.createElement('iframe');
  iframe.setAttribute('aria-label', `sub-app-${appName}`);
  iframe.setAttribute('data-micro-sandbox', 'iframe');
  iframe.setAttribute('style', IFRAME_STYLE);
  // 使用 about:blank 避免额外网络请求，文档立即可用
  iframe.setAttribute('src', 'about:blank');

  parentEl.appendChild(iframe);

  // 同步等待 iframe document 就绪（about:blank 在同源下立即可用）
  const contentWindow = iframe.contentWindow;
  const contentDocument = iframe.contentDocument;

  if (!contentWindow || !contentDocument) {
    // 极端情况下 iframe 未就绪，移除并回退
    iframe.remove();
    throw new Error(`[IframeSandbox:${appName}] Failed to access iframe contentWindow`);
  }

  // 写入基础 HTML 结构，确保有 body 可用
  contentDocument.open();
  contentDocument.write(
    '<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head><body></body></html>',
  );
  contentDocument.close();

  // 复制主应用的基础样式表到 iframe（CSS 变量、设计令牌等）
  // 仅复制 <style> 和 <link> 中带 data-shared-style 标记的，避免全量复制
  try {
    const sharedStyles = document.querySelectorAll(
      'style[data-shared-style], link[data-shared-style]',
    );
    sharedStyles.forEach((node) => {
      contentDocument.head.appendChild(node.cloneNode(true));
    });
  } catch {
    // 样式复制失败不阻断沙箱创建
  }

  // 在 iframe body 内创建挂载容器
  const container = contentDocument.createElement('div');
  container.setAttribute('id', 'subapp-container');
  container.setAttribute('data-micro-app', appName);
  contentDocument.body.appendChild(container);

  let isActive = false;
  let cleaned = false;

  return {
    contentWindow,
    contentDocument,
    container,

    activate() {
      if (isActive || cleaned) return;
      isActive = true;
      if (!import.meta.env.PROD) {
        console.debug(`[IframeSandbox:${appName}] Activated`);
      }
    },

    deactivate() {
      if (!isActive || cleaned) return;
      isActive = false;
      if (!import.meta.env.PROD) {
        console.debug(`[IframeSandbox:${appName}] Deactivated`);
      }
    },

    cleanup() {
      if (cleaned) return;
      cleaned = true;
      isActive = false;

      // 清空 iframe 内容并移除
      try {
        contentDocument.write('');
        contentDocument.close();
      } catch {
        // 忽略清理异常
      }
      iframe.remove();

      if (!import.meta.env.PROD) {
        console.debug(`[IframeSandbox:${appName}] Cleaned up`);
      }
    },
  };
}
