import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { visualizer } from 'rollup-plugin-visualizer'
import { fileURLToPath, URL } from 'node:url'
import { viteMockPlugin } from './src/mock/vite-plugin-mock'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const useMock = env.VITE_USE_MOCK === 'true'
  const analyze = env.ANALYZE === 'true'

  return {
    base: env.VITE_ROUTER_MODE === 'hash' ? './' : '/',

    plugins: [
      vue(),
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        dts: 'auto-imports.d.ts',
        eslintrc: {
          enabled: true,
        },
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'components.d.ts',
      }),
      // P1 批次 20: 独立开发 mock 插件
      // 启用后, 拦截 /api/v1/* 请求直接返回 mock 数据
      // 关闭后, 请求自动 fallback 到 proxy 转发给后端
      viteMockPlugin({
        enabled: useMock,
        delay: Number(env.VITE_MOCK_DELAY || 200),
        verbose: env.VITE_MOCK_VERBOSE === 'true',
      }),
      // P1 批次 20: bundle 体积可视化 (仅在 ANALYZE=true 时启用)
      // 执行 npm run analyze 后, dist/stats.html 可视化查看每个 chunk 占比
      visualizer({
        filename: 'dist/stats.html',
        gzipSize: true,
        brotliSize: true,
        template: 'treemap',
        enabled: analyze,
      }),
    ],

    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
      extensions: ['.ts', '.tsx', '.vue', '.js', '.json'],
    },

    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "@/styles/variables.scss" as *;`,
        },
      },
    },

    server: {
      port: 5173,
      open: true,
      host: '0.0.0.0',
      // 当 mock 关闭时, 代理 /api/v1/* 到后端
      // 当 mock 启用时, viteMockPlugin 已经处理, 这里 proxy 不会触发
      proxy: useMock
        ? undefined
        : {
            '/api': {
              target: env.VITE_API_BASE_URL,
              changeOrigin: true,
              rewrite: (path) => path.replace(/^\/api/, ''),
            },
          },
    },

    build: {
      outDir: 'dist',
      sourcemap: false,
      target: 'es2020',
      minify: 'esbuild',
      chunkSizeWarningLimit: 1500,
      rollupOptions: {
        output: {
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'],
            element: ['element-plus', '@element-plus/icons-vue'],
            echarts: ['echarts'],
            'vxe-table': ['vxe-table', 'xe-utils'],
          },
        },
      },
    },

    define: {
      __VITE_APP_VERSION__: JSON.stringify(process.env.npm_package_version),
    },
  }
})
