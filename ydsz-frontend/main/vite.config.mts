/**
 * main 基座应用的 Vite 构建配置。
 *
 * @remarks
 * 基于 {@code @ydsz/vite-config} 共享配置扩展：启用 CORS 供微前端子应用跨域访问、
 * 按需引入 Element Plus 组件。
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
      server: {
        port: 5600,
        // 允许跨域，微前端子应用需要
        cors: true,
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // 开发环境通过 Gateway 9000 端口统一路由到各后端服务
            target: 'http://localhost:9000',
            ws: true,
          },
        },
      },
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
      ],
    },
  };
});
