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
            // 也可切换为 mock-api: http://localhost:5320/api
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
