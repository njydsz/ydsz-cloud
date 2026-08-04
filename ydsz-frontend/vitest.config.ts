/**
 * Vitest 单元测试配置
 *
 * <p>配置 Vue 3 + JSX 测试环境，使用 happy-dom 作为 DOM 模拟器。
 * 排除 e2e 目录的端到端测试文件。
 *
 * @path vitest.config.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import Vue from '@vitejs/plugin-vue';
import VueJsx from '@vitejs/plugin-vue-jsx';
import { configDefaults, defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [Vue(), VueJsx()],
  test: {
    coverage: {
      enabled: true,
      exclude: [
        '**/e2e/**',
        '**/node_modules/**',
        '**/*.config.{js,ts,mjs,mts}',
        '**/*.d.ts',
        '**/dist/**',
        '**/build/**',
        '**/.{git,idea,vscode}/**',
        '**/coverage/**',
        '**/fixtures/**',
        '**/__mocks__/**',
        '**/types/**',
        '**/mock/**',
      ],
      provider: 'v8',
      reporter: ['text', 'json', 'html', 'lcov', 'clover'],
      reportsDirectory: './coverage',
      // Q1 目标阈值：branches/functions 70%、lines/statements 80%
      // 后续阶段提升路线：Q2 → 80%/85%，Q3 → 85%/90%
      thresholds: {
        perFile: true,
        branches: 70,
        functions: 70,
        lines: 80,
        statements: 80,
      },
    },
    environment: 'happy-dom',
    exclude: [...configDefaults.exclude, '**/e2e/**'],
    reporters: ['default', 'json'],
    outputFile: {
      json: './test-results.json',
    },
  },
});
