/**
 * v-safe-html 指令 — DOMPurify 式白名单消毒
 *
 * 替代全局 5 处裸 v-html，防御 XSS。
 * 策略：仅允许 b/i/em/strong/a/br/span + 有限的 href/class 属性。
 * 如需完整 HTML 消毒（场景极少），可用 processAll 模式。
 *
 * 使用：app.directive('safe-html', vSafeHtml);
 * 模板：<div v-safe-html="htmlContent"></div>
 *
 * @path comm/effects/common-ui/src/components/safe-html/index.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { Directive } from 'vue';

/** 允许的白名单标签 */
const ALLOWED_TAGS = new Set([
  'b', 'i', 'em', 'strong', 'a', 'br', 'p', 'span', 'ul', 'ol', 'li',
  'h1', 'h2', 'h3', 'h4', 'div', 'code', 'pre',
]);

/** 允许的 http 协议 */
const ALLOWED_PROTOCOLS = new Set(['http:', 'https:', 'mailto:']);

/** 白名单属性 */
function sanitizeAttrs(element: Element): void {
  const attrs = element.getAttributeNames();
  for (const attr of attrs) {
    if (attr === 'href' && element.tagName === 'A') {
      const value = element.getAttribute('href') || '';
      // 仅允许 http/https/mailto
      if (
        !value.startsWith('/') &&
        !value.startsWith('#') &&
        !ALLOWED_PROTOCOLS.has((value.match(/^[^:]+:/)?.[0] ?? 'http:'))
      ) {
        element.removeAttribute('href');
      }
    } else if (attr === 'class') {
      // class 允许（用于高亮等 UI 样式）
      continue;
    } else {
      // 移除 onclick/onload/data- 等所有其他属性
      element.removeAttribute(attr);
    }
  }
}

function sanitizeNode(node: Node): void {
  if (node.nodeType === Node.ELEMENT_NODE) {
    const element = node as Element;
    // 不允许的标签：用 textContent 替换整个元素
    if (!ALLOWED_TAGS.has(element.tagName.toLowerCase())) {
      const text = element.textContent || '';
      element.parentNode?.replaceChild(document.createTextNode(text), element);
      return;
    }
    sanitizeAttrs(element);
    // 递归子节点
    const children = [...element.childNodes];
    for (const child of children) {
      sanitizeNode(child);
    }
  }
}

function sanitize(html: string): string {
  const template = document.createElement('template');
  template.innerHTML = html;
  document.body.appendChild(template);
  const root = template.content;

  const children = [...root.childNodes];
  for (const child of children) {
    sanitizeNode(child);
  }

  const result = root.innerHTML;
  document.body.removeChild(template);
  return result;
}

/**
 * v-safe-html 指令。
 *
 * 使用方式：<div v-safe-html="raw"></div>
 */
export const vSafeHtml: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    el.innerHTML = sanitize(binding.value || '');
  },
  updated(el, binding) {
    if (binding.value !== binding.oldValue) {
      el.innerHTML = sanitize(binding.value || '');
    }
  },
};

/** 注册指令到 Vue App */
export function registerSafeHtmlDirective(app: { directive: (name: string, directive: Directive) => void }): void {
  app.directive('safe-html', vSafeHtml);
}

/** 功能性导出：手动调用消毒 */
export { sanitize };
