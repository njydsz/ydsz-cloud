/**
 * common 配置模块
 *
 * @path conf\vite-config\src\config\common.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { UserConfig } from 'vite';

import { defineConfig } from 'vite';

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
