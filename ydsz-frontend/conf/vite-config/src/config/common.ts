/**
 * common 配置模块
 *
 * @path conf\vite-config\src\config\common.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { UserConfig } from 'vite';

import { defineConfig } from 'vite';

/**
 * 构造所有项目共用的基础 Vite 配置。
 *
 * 注入 Node 内置模块的浏览器端 polyfill 别名（buffer/process/stream 等），
 * 并放宽 chunk 体积告警阈值到 2000KB、关闭压缩体积报告以加快构建反馈。
 *
 * @returns 共用基础 Vite 配置对象
 */
async function getCommonConfig(): Promise<UserConfig> {
  return defineConfig({
    define: {
      global: 'globalThis',
    },
    resolve: {
      alias: {
        util: 'rollup-plugin-node-polyfills/polyfills/util',
        buffer: 'rollup-plugin-node-polyfills/polyfills/buffer-es6',
        process: 'rollup-plugin-node-polyfills/polyfills/process-es6',
        events: 'rollup-plugin-node-polyfills/polyfills/events',
        stream: 'rollup-plugin-node-polyfills/polyfills/stream',
        assert: 'rollup-plugin-node-polyfills/polyfills/assert',
        crypto: 'rollup-plugin-node-polyfills/polyfills/crypto-browserify',
      },
    },
    build: {
      chunkSizeWarningLimit: 2000,
      reportCompressedSize: false,
      sourcemap: false,
    },
  });
}

export { getCommonConfig };
