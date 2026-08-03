/**
 * enforce-route-lazy-import 自定义 ESLint 规则
 *
 * @remarks
 * P3 路由级代码分割强制化：在路由定义文件中，`component` / `components` 属性
 * 必须使用动态导入（`() => import('...')`），确保每个路由独立分包。
 *
 * 合法形态：
 * - `component: () => import('#/views/foo.vue')`
 * - `const Foo = () => import('#/views/foo.vue'); component: Foo`
 *
 * 非法形态：
 * - `import Foo from './Foo.vue'; component: Foo`（静态导入）
 * - `component: import('./Foo.vue')`（缺少箭头函数包裹）
 *
 * @path conf/lint-configs/eslint-config/src/rules/enforce-route-lazy-import.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/** ESLint AST 节点类型（宽松类型，避免依赖 @typescript-eslint/utils） */
interface ESNode {
  type: string;
  [key: string]: unknown;
}

interface ESProperty extends ESNode {
  type: 'Property';
  key: ESNode;
  value: ESNode;
}

interface ESIdentifier extends ESNode {
  type: 'Identifier';
  name: string;
}

interface ESArrowFunction extends ESNode {
  type: 'ArrowFunctionExpression';
  body: ESNode;
}

interface ESVariableDeclarator extends ESNode {
  type: 'VariableDeclarator';
  init?: ESNode | null;
}

interface ESVariableDef {
  node: ESNode;
}

interface ESVariable {
  defs: ESVariableDef[];
}

interface ESScope {
  set: Map<string, ESVariable>;
}

/** 检查节点是否为合法的动态导入形态（箭头函数包裹 import()） */
function isLazyImportArrow(node: ESNode | undefined | null): boolean {
  if (!node || node.type !== 'ArrowFunctionExpression') return false;

  const body = (node as ESArrowFunction).body;
  // () => import('...')
  if (body.type === 'ImportExpression') return true;

  // () => { return import('...') }
  if (body.type === 'BlockStatement') {
    const blockBody = (body as { body: ESNode[] }).body;
    if (
      blockBody.length === 1 &&
      blockBody[0].type === 'ReturnStatement'
    ) {
      const arg = (blockBody[0] as { argument?: ESNode }).argument;
      if (arg?.type === 'ImportExpression') return true;
    }
  }

  return false;
}

/**
 * 通过作用域解析 Identifier 是否指向合法的 lazy import 包装。
 *
 * 例如：`const BasicLayout = () => import('./basic.vue')` 中的 BasicLayout。
 */
function isIdentifierLazyImport(
  node: ESIdentifier,
  context: { sourceCode: { getScope: (node: ESNode) => ESScope } },
): boolean {
  const scope = context.sourceCode.getScope(node);
  const variable = scope?.set?.get(node.name);

  if (!variable || variable.defs.length === 0) return false;

  const def = variable.defs[0];
  // const X = () => import(...)
  if (def.node.type === 'VariableDeclarator') {
    const init = (def.node as ESVariableDeclarator).init;
    return isLazyImportArrow(init);
  }

  return false;
}

/** ESLint 规则定义对象（兼容 flat config 内联插件） */
export const enforceRouteLazyImportRule = {
  create(context: {
    sourceCode: { getScope: (node: ESNode) => ESScope };
    report: (descriptor: {
      messageId: string;
      node: ESNode;
      data?: Record<string, string>;
    }) => void;
  }) {
    return {
      Property(node: ESProperty) {
        const key = node.key as ESIdentifier;
        if (key.type !== 'Identifier') return;
        if (key.name !== 'component' && key.name !== 'components') return;

        const value = node.value;

        // 1. 箭头函数包裹 import() → 合法
        if (isLazyImportArrow(value)) return;

        // 2. 标识符 → 解析作用域，检查是否为 lazy import 包装
        if (value.type === 'Identifier') {
          if (isIdentifierLazyImport(value as ESIdentifier, context)) return;

          context.report({
            data: { name: (value as ESIdentifier).name },
            messageId: 'requireDynamicImport',
            node: value,
          });
          return;
        }

        // 3. 直接 import() 表达式（缺少箭头函数包裹）→ 报错
        // 4. 其他形态 → 报错
        context.report({
          messageId: 'requireDynamicImport',
          node: value,
        });
      },
    };
  },
  meta: {
    docs: {
      description:
        'Enforce route component properties to use dynamic imports for code splitting',
    },
    messages: {
      requireDynamicImport:
        'P3 路由级代码分割：component/components 属性必须使用动态导入 `() => import(...)`。{{name}} 疑似为静态导入的组件，请改为懒加载。',
    },
    schema: [],
    type: 'problem',
  },
};
