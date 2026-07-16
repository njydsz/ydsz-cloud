/* eslint-env node */
module.exports = {
  root: true,
  env: {
    browser: true,
    node: true,
    es2022: true,
  },
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 2022,
    sourceType: 'module',
    extraFileExtensions: ['.vue'],
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    'plugin:@typescript-eslint/recommended',
    'prettier',
  ],
  plugins: ['@typescript-eslint', 'vuejs-accessibility'],
  rules: {
    'vue/multi-word-component-names': 'off',
    'vue/no-v-html': 'warn',
    'vue/component-definition-name-casing': ['error', 'PascalCase'],
    'vue/attribute-hyphenation': ['error', 'always'],
    'vue/v-on-event-hyphenation': ['error', 'always'],
    // P2-10: a11y 合规规则（vuejs-accessibility 插件）
    'vuejs-accessibility/alt-text': 'error',
    'vuejs-accessibility/aria-props': 'error',
    'vuejs-accessibility/aria-role': 'error',
    'vuejs-accessibility/aria-unsupported-elements': 'error',
    'vuejs-accessibility/click-events-have-key-events': 'warn',
    'vuejs-accessibility/form-control-has-label': 'error',
    'vuejs-accessibility/heading-has-content': 'error',
    'vuejs-accessibility/iframe-has-title': 'error',
    'vuejs-accessibility/img-redundant-alt': 'warn',
    'vuejs-accessibility/interactive-supports-focus': 'warn',
    'vuejs-accessibility/label-has-for': 'error',
    'vuejs-accessibility/media-has-caption': 'warn',
    'vuejs-accessibility/mouse-events-have-key-events': 'warn',
    'vuejs-accessibility/no-access-key': 'error',
    'vuejs-accessibility/no-autofocus': 'warn',
    'vuejs-accessibility/no-distracting-elements': 'error',
    'vuejs-accessibility/no-redundant-roles': 'warn',
    'vuejs-accessibility/role-has-required-aria-props': 'error',
    'vuejs-accessibility/role-supports-aria-props': 'error',
    'vuejs-accessibility/tabindex-no-positive': 'warn',
    '@typescript-eslint/no-unused-vars': [
      'error',
      {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_',
      },
    ],
    // 批次 19 P2-4：any 收口（warn → error 强制不允许 any 出现在生产代码）
    // 极个别遗留可加 eslint-disable-next-line 注明 TODO
    // 批次 20 调整: 暂降为 warn (生产代码仍有 100+ 处遗留 any, 需逐项收口)
    // 批次 22+: 目标回归 error, 并在 CI 中作为门禁
    '@typescript-eslint/no-explicit-any': 'warn',
    '@typescript-eslint/no-unsafe-argument': 'off',
    '@typescript-eslint/no-unsafe-assignment': 'off',
    '@typescript-eslint/no-unsafe-member-access': 'off',
    '@typescript-eslint/no-unsafe-return': 'off',
    '@typescript-eslint/explicit-module-boundary-types': 'off',
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': 'error',
    'no-var': 'error',
    eqeqeq: ['error', 'always'],
    'prefer-const': 'error',
  },
  overrides: [
    {
      // P2-1: 核心基础设施模块强制禁止 any（已全部收口）
      // 这些模块被业务代码广泛复用，类型安全必须保证
      files: [
        'src/components/common/**',
        'src/utils/**',
        'src/composables/**',
        'src/store/**',
      ],
      rules: {
        '@typescript-eslint/no-explicit-any': 'error',
      },
    },
    {
      // 允许特定目录继续使用 any（过渡期，可选）
      files: [
        'src/api/**/index.ts',
        'src/views/execution/reconcile/**',
        'src/views/execution/alert/**',
        // 测试文件: vi.fn() 返回 any, mock 数据通常用 any
        'src/**/__tests__/**',
        'src/**/*.test.ts',
        'src/**/*.spec.ts',
        'e2e/**',
        'src/utils/request.ts',
        'src/main.ts',
        'src/App.vue',
      ],
      rules: {
        '@typescript-eslint/no-explicit-any': 'off',
        '@typescript-eslint/no-unused-vars': 'off',
        'vue/require-default-prop': 'off',
        'no-empty-pattern': 'off',
      },
    },
    {
      // mock 文件: 允许 any 与未使用变量
      files: ['src/mock/**', '**/mock/**'],
      rules: {
        '@typescript-eslint/no-explicit-any': 'off',
      },
    },
  ],
  ignorePatterns: ['dist', 'node_modules', 'coverage', 'auto-imports.d.ts', 'components.d.ts'],
}
