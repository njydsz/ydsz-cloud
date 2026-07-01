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
  plugins: ['@typescript-eslint'],
  rules: {
    'vue/multi-word-component-names': 'off',
    'vue/no-v-html': 'warn',
    'vue/component-definition-name-casing': ['error', 'PascalCase'],
    'vue/attribute-hyphenation': ['error', 'always'],
    'vue/v-on-event-hyphenation': ['error', 'always'],
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
    '@typescript-eslint/no-explicit-any': 'error',
    '@typescript-eslint/no-unsafe-argument': 'warn',
    '@typescript-eslint/no-unsafe-assignment': 'warn',
    '@typescript-eslint/no-unsafe-member-access': 'warn',
    '@typescript-eslint/no-unsafe-return': 'warn',
    '@typescript-eslint/explicit-module-boundary-types': 'off',
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': 'error',
    'no-var': 'error',
    eqeqeq: ['error', 'always'],
    'prefer-const': 'error',
  },
  overrides: [
    {
      // 允许特定目录继续使用 any（过渡期，可选）
      files: [
        'src/api/**/index.ts',
        'src/views/execution/reconcile/**',
        'src/views/execution/alert/**'
      ],
      rules: {
        '@typescript-eslint/no-explicit-any': 'warn'
      }
    }
  ],
  ignorePatterns: ['dist', 'node_modules', 'coverage', 'auto-imports.d.ts', 'components.d.ts'],
}
