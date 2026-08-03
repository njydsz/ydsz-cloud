/**
 * 沙箱隔离约束规则配置
 *
 * <p>禁止子应用直接修改全局对象（window/document），防止运行时污染。
 * <p>配合 micro-kernel 快照沙箱使用，确保子应用隔离安全。
 *
 * @path conf/lint-configs/eslint-config/src/configs/sandbox.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import type { Linter } from 'eslint';

/**
 * 沙箱隔离约束 ESLint 规则
 *
 * <p>限制子应用直接访问和修改全局对象，强制使用框架提供的 API。
 * <p>适用于 apps/ 目录下的所有子应用代码。
 */
export async function sandbox(): Promise<Linter.Config[]> {
  return [
    {
      name: 'ydsz/sandbox/isolation',
      files: ['apps/**/*.ts', 'apps/**/*.tsx', 'apps/**/*.vue'],
      rules: {
        // 禁止直接修改 window 对象
        'no-restricted-properties': [
          'error',
          {
            object: 'window',
            property: 'addEventListener',
            message: '子应用禁止直接调用 window.addEventListener，请使用框架提供的全局状态管理 API。',
          },
          {
            object: 'window',
            property: 'removeEventListener',
            message: '子应用禁止直接调用 window.removeEventListener，请使用框架提供的全局状态管理 API。',
          },
          {
            object: 'window',
            property: 'setTimeout',
            message: '子应用禁止直接调用 window.setTimeout，请使用框架提供的定时器管理 API。',
          },
          {
            object: 'window',
            property: 'setInterval',
            message: '子应用禁止直接调用 window.setInterval，请使用框架提供的定时器管理 API。',
          },
          {
            object: 'window',
            property: 'clearTimeout',
            message: '子应用禁止直接调用 window.clearTimeout，请使用框架提供的定时器管理 API。',
          },
          {
            object: 'window',
            property: 'clearInterval',
            message: '子应用禁止直接调用 window.clearInterval，请使用框架提供的定时器管理 API。',
          },
        ],
        // 禁止直接赋值给 window 属性
        'no-restricted-syntax': [
          'error',
          {
            selector: 'AssignmentExpression[left.object.name="window"]',
            message: '子应用禁止直接修改 window 属性，请使用框架提供的全局状态管理 API。',
          },
          {
            selector: 'AssignmentExpression[left.object.name="document"]',
            message: '子应用禁止直接修改 document 属性，请使用框架提供的 DOM 操作 API。',
          },
          {
            selector: 'MemberExpression[object.name="window"][computed=false][property.name="location"]',
            message: '子应用禁止直接访问 window.location，请使用 Vue Router 提供的路由 API。',
          },
          {
            selector: 'MemberExpression[object.name="window"][computed=false][property.name="history"]',
            message: '子应用禁止直接访问 window.history，请使用 Vue Router 提供的路由 API。',
          },
        ],
        // 禁止使用 eval 和 Function 构造器（防止动态代码执行污染全局）
        'no-eval': 'error',
        'no-implied-eval': 'error',
        // 禁止未声明的变量（防止意外创建全局变量）
        'no-undef': 'error',
      },
    },
  ];
}
