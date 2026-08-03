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
 * 历史曾注入 buffer/process/stream/crypto 等 Node 内置模块的浏览器 polyfill，
 * 审计确认无源码消费（浏览器原生 Web Crypto API 足以替代 crypto-browserify），
 * 已于 v3.1 移除以削减产物体积与构建耗时。
 *
 * 保留 global→globalThis 映射，兼容少数第三方库对 Node global 的引用。
 *
 * @returns 共用基础 Vite 配置对象
 */
async function getCommonConfig(): Promise<UserConfig> {
  return defineConfig({
    define: {
      global: 'globalThis',
    },
    build: {
      chunkSizeWarningLimit: 1000,
      reportCompressedSize: false,
      sourcemap: false,
    },
  });
}

export { getCommonConfig };
