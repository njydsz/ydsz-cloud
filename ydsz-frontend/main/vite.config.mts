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
    application: {
      // 启用 PWA 支持（Service Worker 离线缓存）
      pwa: true,
      pwaOptions: {
        // 自定义 Workbox 配置（已在 vite-config 中配置默认值）
        workbox: {
          // 预缓存 HTML 入口
          globPatterns: ['**/*.{html,js,css}'],
        },
        manifest: {
          name: 'YDSZ PMIS',
          short_name: 'YDSZ',
          description: 'YDSZ 项目管理系统',
          theme_color: '#409eff',
          background_color: '#ffffff',
        },
      },
    },
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
