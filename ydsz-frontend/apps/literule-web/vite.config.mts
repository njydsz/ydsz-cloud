/**
 * 规则引擎子应用 Vite 构建配置。
 *
 * @remarks
 * 基于 @ydsz/vite-config 统一配置，接入 ElementPlus 插件；
 * 开发服务器固定端口 5608，/api 请求代理至本地 9000 端口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
import { defineConfig } from '@ydsz/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      base: '/',
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
      ],
      server: {
        port: 5608,
        cors: true,
        host: '0.0.0.0',
        headers: {
          'Access-Control-Allow-Origin': '*',
        },
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            target: 'http://localhost:9000',
            ws: true,
          },
        },
      },
    },
  };
});
