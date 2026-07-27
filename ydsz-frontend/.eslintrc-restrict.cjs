/**
 * P3-2: 前端 ESLint 等效约束规则
 *
 * 禁止子应用中出现已抽取到公共包的重复文件。
 * 子应用应从 @ydsz/shared-auth 导入，而非维护本地副本。
 *
 * 使用方式：在根 .eslintrc.cjs 中 extends 此配置，或在 CI 中执行：
 *   eslint --rule 'no-restricted-paths: [error, {zones: [...]}]' apps/*/src
 */

/** @type {import('eslint').Linter.Config} */
module.exports = {
  rules: {
    // 禁止子应用 api/ 目录下直接定义 axios 实例
    'no-restricted-syntax': [
      'error',
      {
        // 禁止在子应用中直接 import axios 或创建 RequestClient 实例
        selector: "ImportDeclaration[source.value='axios']",
        message: '禁止直接 import axios，请从 @ydsz/shared-auth 导入 requestClient',
      },
    ],
    'no-restricted-imports': [
      'error',
      {
        // 子应用不能从 @ydsz/shared-auth 以外的包导入认证相关 API
        patterns: [
          {
            group: ['#/api/core/auth'],
            message: '请从 @ydsz/shared-auth 直接导入，而非子应用本地副本',
          },
        ],
      },
    ],
  },
  overrides: [
    {
      // 仅对子应用目录生效
      files: ['apps/*/src/api/core/{auth,user,menu}.ts'],
      rules: {
        // 子应用 core 文件只允许 re-export，不允许定义实际逻辑
        'max-lines': ['error', { max: 10, ignoreComments: true }],
      },
    },
    {
      files: ['apps/*/src/api/request.ts'],
      rules: {
        'max-lines': ['error', { max: 10, ignoreComments: true }],
      },
    },
  ],
};
