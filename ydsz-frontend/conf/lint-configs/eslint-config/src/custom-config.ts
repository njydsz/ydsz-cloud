/**
 * custom-config 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\custom-config.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

/** 对这些配置文件放宽导入限制（避免与构建期脚本冲突） */
const restrictedImportIgnores = [
  '**/vite.config.mts',
  '**/tailwind.config.mjs',
  '**/postcss.config.mjs',
];

/**
 * 项目自定义的 ESLint 规则集合。
 *
 * 按目录（apps / @core / comm 等）施加差异化的导入限制与规则开关，
 * 例如禁止 @core 反向依赖 @ydsz、统一子包边界；restrictedImportIgnores 中的
 * 构建配置文件则不受这些限制约束。
 */
const customConfig: Linter.Config[] = [
  // shadcn-ui 内部组件是自动生成的，不做太多限制
  {
    files: ['comm/@core/ui-kit/shadcn-ui/**/**'],
    rules: {
      'vue/require-default-prop': 'off',
    },
  },
  {
    files: [
      'apps/**/**',
      'comm/effects/**/**',
      'comm/utils/**/**',
      'comm/types/**/**',
      'comm/locales/**/**',
    ],
    ignores: restrictedImportIgnores,
    rules: {
      'perfectionist/sort-interfaces': 'off',
      'perfectionist/sort-objects': 'off',
    },
  },
  {
    files: ['**/**.vue'],
    ignores: restrictedImportIgnores,
    rules: {
      'perfectionist/sort-objects': 'off',
    },
  },
  {
    // apps内部的一些基础规则
    files: ['apps/**/**'],
    ignores: restrictedImportIgnores,
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['#/api/*'],
              message:
                'The #/api package cannot be imported, please use the @core package itself',
            },
            {
              group: ['#/layouts/*'],
              message:
                'The #/layouts package cannot be imported, please use the @core package itself',
            },
            {
              group: ['#/locales/*'],
              message:
                'The #/locales package cannot be imported, please use the @core package itself',
            },
            {
              group: ['#/stores/*'],
              message:
                'The #/stores package cannot be imported, please use the @core package itself',
            },
          ],
        },
      ],
      'perfectionist/sort-interfaces': 'off',
    },
  },
  {
    // @core内部组件，不能引入@ydsz/* 里面的包
    files: ['comm/@core/**/**'],
    ignores: restrictedImportIgnores,
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@ydsz/*'],
              message:
                'The @core package cannot import the @ydsz package, please use the @core package itself',
            },
          ],
        },
      ],
    },
  },
  {
    // @core/shared内部组件，不能引入@ydsz/* 或者 @ydsz-core/* 里面的包
    files: ['comm/@core/base/**/**'],
    ignores: restrictedImportIgnores,
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@ydsz/*', '@ydsz-core/*'],
              message:
                'The @ydsz-core/shared package cannot import the @ydsz package, please use the @core/shared package itself',
            },
          ],
        },
      ],
    },
  },

  {
    // 不能引入@ydsz/*里面的包
    files: [
      'comm/types/**/**',
      'comm/utils/**/**',
      'comm/icons/**/**',
      'comm/constants/**/**',
      'comm/styles/**/**',
      'comm/stores/**/**',
      'comm/preferences/**/**',
      'comm/locales/**/**',
    ],
    ignores: restrictedImportIgnores,
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@ydsz/*'],
              message:
                'The @ydsz package cannot be imported, please use the @core package itself',
            },
          ],
        },
      ],
    },
  },
  // 后端模拟代码，不需要太多规则
  {
    files: ['docs/**/**'],
    rules: {
      '@typescript-eslint/no-extraneous-class': 'off',
      'n/no-extraneous-import': 'off',
      'n/prefer-global/buffer': 'off',
      'n/prefer-global/process': 'off',
      'no-console': 'off',
      'unicorn/prefer-module': 'off',
    },
  },
  {
    files: ['**/**/playwright.config.ts'],
    rules: {
      'n/prefer-global/buffer': 'off',
      'n/prefer-global/process': 'off',
      'no-console': 'off',
    },
  },
  {
    files: ['conf/**/**', 'bash/**/**'],
    rules: {
      'no-console': 'off',
    },
  },
  // === 微前端架构约束 ===
  {
    // 子应用中禁止引入 shadcn-ui / radix-vue（组件体系收敛为 Element Plus + vxe-table）
    files: ['apps/**/**', 'main/**/**'],
    ignores: restrictedImportIgnores,
    rules: {
      'no-restricted-imports': [
        'error',
        {
          paths: [
            {
              name: '@ydsz-core/ui-kit/shadcn-ui',
              message: '[架构] 新页面禁止引入 shadcn-ui。统一使用 Element Plus 组件，存量代码随迭代逐步替换。',
            },
            {
              name: 'radix-vue',
              message: '[架构] 禁止直接引入 radix-vue，使用 Element Plus 对应组件替代。',
            },
          ],
        },
      ],
    },
  },
  {
    // 子应用中禁止直接写 window 全局变量（依赖 micro-kernel 快照沙箱兜底，lint 前置拦截）
    files: ['apps/**/**'],
    ignores: restrictedImportIgnores,
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: "AssignmentExpression[left.type='MemberExpression'][left.object.type='Identifier'][left.object.name='window']",
          message: '[沙箱] 禁止子应用中直接给 window 挂属性。如需跨应用通信请使用 globalState 或对应的 Pinia store。',
        },
      ],
    },
  },
];

export { customConfig };
