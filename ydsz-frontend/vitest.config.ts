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
    environment: 'happy-dom',
    exclude: [...configDefaults.exclude, '**/e2e/**'],
  },
});
