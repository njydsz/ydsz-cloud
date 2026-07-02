/**
 * @file vite.config.ts
 * @description Vite 构建配置入口, 负责开发服务器、构建产物、插件体系、Mock 拦截等能力。
 *              通过环境变量 (.env / .env.[mode]) 控制是否启用 Mock、Bundle 分析等开关。
 * @module ydsz-pmis-frontend/vite.config.ts
 */
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { visualizer } from 'rollup-plugin-visualizer'
import viteCompression from 'vite-plugin-compression'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'
import { viteMockPlugin } from './src/mock/vite-plugin-mock'

export default defineConfig(({ mode }) => {
  // 加载 .env / .env.[mode] 文件中的环境变量 (前缀不做限制, 全量加载)
  const env = loadEnv(mode, process.cwd(), '')
  // 是否启用前端 Mock 拦截 (VITE_USE_MOCK=true 时启用)
  const useMock = env.VITE_USE_MOCK === 'true'
  // 是否开启 bundle 体积分析 (ANALYZE=true 时启用)
  const analyze = env.ANALYZE === 'true'

  return {
    // hash 路由模式下使用相对路径, 以便部署到子路径; history 模式使用根路径
    base: env.VITE_ROUTER_MODE === 'hash' ? './' : '/',

    plugins: [
      // Vue SFC 编译插件
      vue(),
      // 自动导入 Vue / Vue Router / Pinia 的 API, 免去手动 import ref/reactive 等
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver()],
        dts: 'auto-imports.d.ts',
        eslintrc: {
          enabled: true,
        },
      }),
      // 自动按需注册 Element Plus 组件, 配合 ts 声明文件提供类型提示
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
      // P3-28: PWA 离线缓存与可安装性
      // registerType: 'auto-update' 自动更新 Service Worker
      // manifest 定义应用图标、主题色等, 支持添加到主屏幕
      // workbox 缓存静态资源, 实现离线访问
      VitePWA({
        registerType: 'auto-update',
        includeAssets: ['favicon.ico', 'robots.txt'],
        manifest: {
          name: 'YDSZ PMIS 项目管理系统',
          short_name: 'PMIS',
          description: '企业级项目管理系统',
          theme_color: '#409eff',
          background_color: '#ffffff',
          icons: [
            { src: '/pwa-192x192.png', sizes: '192x192', type: 'image/png' },
            { src: '/pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          ],
        },
        workbox: {
          globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
          navigateFallback: '/index.html',
        },
      }),
      // P2-5: 生产构建 gzip + brotli 压缩
      // 生成 .gz 和 .br 静态文件, 配合 Nginx gzip_static/brotli_static 可减少 60-80% 传输体积
      ...(mode === 'production'
        ? [
            viteCompression({
              algorithm: 'gzip',
              ext: '.gz',
              threshold: 10240, // 仅压缩 >10KB 的文件
              deleteOriginFile: false, // 保留原始文件
            }),
            viteCompression({
              algorithm: 'brotliCompress',
              ext: '.br',
              threshold: 10240,
              deleteOriginFile: false,
            }),
          ]
        : []),
    ],

    resolve: {
      // @ 别名指向 src 目录, 简化业务代码中的 import 路径
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
      // 解析时省略后缀的优先级: ts > tsx > vue > js > json
      extensions: ['.ts', '.tsx', '.vue', '.js', '.json'],
    },

    css: {
      preprocessorOptions: {
        scss: {
          // 全局注入 SCSS 变量, 业务样式可直接使用 variables.scss 中定义的变量
          additionalData: `@use "@/styles/variables.scss" as *;`,
        },
      },
    },

    server: {
      port: 5173, // 开发服务器端口
      open: true, // 启动后自动打开浏览器
      host: '0.0.0.0', // 监听所有网卡, 便于容器 / 局域网访问
      // 当 mock 关闭时, 代理 /api/v1/* 到后端
      // 当 mock 启用时, viteMockPlugin 已经处理, 这里 proxy 不会触发
      proxy: useMock
        ? undefined
        : {
            '/api': {
              target: env.VITE_API_BASE_URL, // 后端服务地址 (VITE_API_BASE_URL)
              changeOrigin: true, // 修改请求头 Host, 避免跨域校验失败
              rewrite: (path) => path.replace(/^\/api/, ''), // 剥离 /api 前缀后转发
            },
          },
    },

    build: {
      outDir: 'dist', // 产物输出目录
      sourcemap: false, // 生产环境不输出 sourcemap, 减小产物体积
      target: 'es2020', // 编译目标: ES2020 (可选链 / 空值合并等原生支持)
      minify: 'esbuild', // 使用 esbuild 压缩, 速度优于 terser
      chunkSizeWarningLimit: 1500, // chunk 体积告警阈值 (kB), 兼容 vxe-table 等大依赖
      rollupOptions: {
        output: {
          // 手动拆分 vendor chunk, 提升缓存命中率并减少首屏体积
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia'], // Vue 全家桶
            element: ['element-plus', '@element-plus/icons-vue'], // Element Plus UI 库
            echarts: ['echarts'], // 图表库
            'vxe-table': ['vxe-table', 'xe-utils'], // 高性能表格
          },
        },
      },
    },

    define: {
      // 注入应用版本号, 业务代码可通过 __VITE_APP_VERSION__ 读取 (取自 package.json)
      __VITE_APP_VERSION__: JSON.stringify(process.env.npm_package_version),
    },
  }
})
