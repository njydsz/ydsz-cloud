/**
 * PostCSS 插件：为子应用 CSS 添加作用域前缀。
 *
 * 约定：每个子应用挂载时，micro-kernel 在容器上设置 `data-micro-app="app-name"` 属性。
 * 本插件将子应用所有 CSS 规则的选择器前追加 `[data-micro-app="app-name"]`，
 * 使样式仅作用于对应子应用的 DOM 树，防止跨应用样式污染。
 *
 * 仅处理构建产物（Vite build），开发模式下 Vite dev server 自带 HMR 作用域隔离。
 *
 * @path conf/vite-config/src/plugins/postcss-micro-scoped.ts
 * @author ydsz-team
 * @since 3.0.0
 */

import type { AcceptedPlugin, Rule, AtRule, ChildNode } from 'postcss';

export interface MicroCssScopeOptions {
  /** 子应用名称（如 'project-web'），作为 data-micro-app 属性值 */
  appName: string;
}

/**
 * 创建子应用 CSS 作用域 PostCSS 插件。
 *
 * 原理：遍历 AST 中的所有规则，为每个选择器前追加属性选择器。
 * 示例：`.btn { color: red; }` → `[data-micro-app="project-web"] .btn { color: red; }`
 *
 * @param options - 插件选项（应用名）
 * @returns PostCSS 插件实例
 */
export function postcssMicroScopedPlugin(options: MicroCssScopeOptions): AcceptedPlugin {
  const prefix = `[data-micro-app="${options.appName}"]`;

  return {
    postcssPlugin: 'ydsz:micro-css-scoped',

    /** 处理普通规则（.class、#id、tag 等） */
    Rule(rule: Rule) {
      // 跳过已处理过的规则（避免重复前缀）
      if (rule.parent?.type === 'atrule') {
        const atRule = rule.parent as AtRule;
        if (atRule.name === 'keyframes' || atRule.name === '-webkit-keyframes') return;
      }

      try {
        rule.selectors = rule.selectors.map((selector) => {
          const trimmed = selector.trim();
          // 跳过已包含 scope 前缀的规则
          if (trimmed.startsWith(prefix)) return selector;
          // 跳过 body/html 选择器（全局重置类规则不移 scoped）
          if (trimmed === 'body' || trimmed === 'html' || trimmed.startsWith('body ') || trimmed.startsWith('html ')) {
            return selector;
          }
          // 跳过 :root 选择器
          if (trimmed === ':root' || trimmed.startsWith(':root')) return selector;

          return `${prefix} ${selector}`;
        });
      } catch {
        // 选择器解析失败时静默跳过（PostCSS 选择器语法复杂，不影响构建）
      }
    },

    /** 处理 @media / @supports 等嵌套规则 */
    AtRule: {
      media(atRule: AtRule & { nodes?: ChildNode[] }) {
        if (!atRule.nodes) return;
        // 递归处理 @media 内的规则（PostCSS 的 Rule hook 会自动遍历）
      },
    },
  };
}

// PostCSS 插件需要静态 postcssPlugin 属性
postcssMicroScopedPlugin.postcss = true;
