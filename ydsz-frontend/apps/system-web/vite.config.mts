import { defineConfig } from '@ydsz/vite-config';

import ElementPlus from 'unplugin-element-plus/vite';
import qiankun from 'vite-plugin-qiankun';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      base: '/',
      plugins: [
        ElementPlus({
          format: 'esm',
        }),
        qiankun('system-web', {
          useDevMode: true,
        }),
      ],
      server: {
        port: 5602,
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
