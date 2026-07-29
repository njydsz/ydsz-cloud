/**
 * 前端 ESLint 约束规则
 *
 * 禁止子应用中出现已抽取到公共包的重复代码：
 * 1. 禁止子应用直接 import axios（应从 @ydsz/shared-auth 导入 requestClient）
 * 2. 禁止子应用封装通用 CRUD 工厂（createCrudApi / CrudApi 等）
 * 3. 禁止子应用直接实现 auth 逻辑（应从 @ydsz/shared-auth 导入）
 * 4. 子应用 core/request 文件只允许 re-export（max-lines 限制）
 *
 * 使用方式：在根 .eslintrc.cjs 中 extends 此配置，或在 CI 中执行：
 *   eslint --rule 'no-restricted-paths: [error, {zones: [...]}]' apps/*/src
 */

/** @type {import('eslint').Linter.Config} */
module.exports = {
  rules: {
    // ── 规则 1: 禁止直接 import axios ──
    'no-restricted-syntax': [
      'error',
      {
        // 禁止在子应用中直接 import axios 或创建 RequestClient 实例
        selector: "ImportDeclaration[source.value='axios']",
        message: '禁止直接 import axios，请从 @ydsz/shared-auth 导入 requestClient',
      },
      {
        // 禁止子应用直接定义 login/logout/refreshToken/getUserInfo/getMenuRoutes 等认证方法
        selector:
          "ExportNamedDeclaration > FunctionDeclaration[id.name=/^(login|logout|refreshToken|getUserInfo|getUserInfoApi|getAllMenusApi|getMenuTreeApi|getAccessCodesApi|loginApi|logoutApi|refreshTokenApi)$/]",
        message:
          '禁止子应用直接实现认证逻辑，请从 @ydsz/shared-auth 导入对应方法',
      },
    ],

    // ── 规则 2: 禁止从非公共包导入认证相关 API ──
    'no-restricted-imports': [
      'error',
      {
        // 子应用不能从 @ydsz/shared-auth 以外的包导入认证相关 API
        patterns: [
          {
            group: ['#/api/core/auth'],
            message: '请从 @ydsz/shared-auth 直接导入，而非子应用本地副本',
          },
          {
            group: ['#/api/core/user'],
            message: '请从 @ydsz/shared-auth 直接导入，而非子应用本地副本',
          },
          {
            group: ['#/api/core/menu'],
            message: '请从 @ydsz/shared-auth 直接导入，而非子应用本地副本',
          },
          {
            group: ['#/api/request'],
            message: '请从 @ydsz/shared-auth 导入 requestClient，而非子应用本地副本',
          },
          {
            group: ['#/store/auth'],
            message: '请从 @ydsz/shared-auth 导入 createSharedAuthStore，而非子应用本地副本',
          },
          {
            group: ['@ydsz/shared-api'],
            message:
              '@ydsz/shared-api 已删除，禁止封装通用 CRUD 工厂。每个 API 文件应显式定义各个 API 方法（使用 requestClient.get/post/put/delete）',
          },
        ],
      },
    ],
  },

  overrides: [
    // ── 子应用 core 文件只允许 re-export ──
    {
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
    {
      files: ['apps/*/src/store/auth.ts'],
      rules: {
        'max-lines': ['error', { max: 10, ignoreComments: true }],
      },
    },

    // ── 子应用 store 文件禁止直接实现 auth store ──
    {
      files: ['apps/*/src/store/**/*.ts'],
      excludedFiles: ['apps/*/src/store/auth.ts'],
      rules: {
        'no-restricted-syntax': [
          'error',
          {
            selector:
              "VariableDeclaration > VariableDeclarator[id.name='authStore'] > CallExpression[callee.name!='createSharedAuthStore']",
            message:
              '禁止子应用直接创建 authStore，请从 @ydsz/shared-auth 导入 createSharedAuthStore',
          },
        ],
      },
    },
  ],
};
