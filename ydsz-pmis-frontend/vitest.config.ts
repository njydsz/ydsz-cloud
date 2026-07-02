/**
 * @file vitest.config.ts
 * @description Vitest 单元测试配置, 基于 Vite 构建 pipeline 运行.
 *              负责测试环境 (jsdom)、用例匹配规则、setup 文件以及覆盖率收集策略.
 *              通过 pnpm run test:unit 执行, 覆盖率产物输出至 coverage/ 目录.
 * @module ydsz-pmis-frontend/vitest.config.ts
 */
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  // 复用 Vue 插件以支持 SFC 内 <script> 单测
  plugins: [vue()],
  resolve: {
    // 与 vite.config.ts 保持一致的 @ 别名, 保证测试代码与业务代码路径写法统一
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    globals: true, // 注入全局 API (describe/it/expect), 免去逐文件 import
    environment: 'jsdom', // 模拟 DOM 环境, 支持组件挂载与交互断言
    setupFiles: ['./src/tests/setup.ts'], // 测试前置脚本 (mock 全局对象、引入插件等)
    // 用例匹配规则: 任意目录下的 *.test.ts / *.spec.ts, 以及 __tests__ 目录下所有文件
    include: ['src/**/*.{test,spec}.{ts,js}', 'src/**/__tests__/**/*.{ts,js}'],
    coverage: {
      provider: 'v8', // 使用 V8 原生覆盖率, 性能优于 istanbul
      reporter: ['text', 'html', 'lcov'], // 终端文本 + HTML 报告 + lcov (供 CI 消费)
      // 排除非业务代码: 三方依赖、构建产物、类型声明、入口文件、桶文件、配置文件、测试自身
      exclude: [
        'node_modules/',
        'dist/',
        'src/**/*.d.ts',
        'src/main.ts',
        'src/**/index.ts',
        'src/**/types.ts',
        'src/**/*.config.{ts,js}',
        'src/tests/**',
      ],
    },
  },
})
