import { defineConfig } from '@ydsz/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        // 允许跨域，微前端子应用需要
        cors: true,
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // mock代理目标地址
            target: 'http://localhost:5320/api',
            ws: true,
          },
        },
      },
    },
  };
});
