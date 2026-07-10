/**
 * @file vite.config.ts
 * @description Vite 构建配置入口, 负责开发服务器、构建产物、插件体系、Mock 拦截等能力。
 *              通过环境变量 (.env / .env.[mode]) 控制是否启用 Mock、Bundle 分析等开关。
 * @module ydsz-pmis-frontend/vite.config.ts
 */
import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { visualizer } from 'rollup-plugin-visualizer'
import viteCompression from 'vite-plugin-compression'
import viteImagemin from 'vite-plugin-imagemin'
import { VitePWA } from 'vite-plugin-pwa'
import { fileURLToPath, URL } from 'node:url'
import { viteMockPlugin } from './src/mock/vite-plugin-mock'
// P2-6: CDN 外置依赖清单（仅纯数据，兼容 Node 环境导入）
import { CDN_DEPS } from './src/config/cdn'

/**
 * P2-6: CDN 外置 - index.html 注入插件
 * 仅在启用 CDN 时（生产环境 + VITE_CDN_ENABLED !== 'false'）向 index.html 注入
 * CDN 的 <script> 与 <link> 标签，替换占位符 <!-- CDN_INJECT -->。
 * 开发环境不注入，仍走 node_modules 本地加载。
 */
function injectCdn(enabled: boolean) {
  return {
    name: 'inject-cdn',
    transformIndexHtml(html: string) {
      if (!enabled) return html
      const styles = CDN_DEPS.flatMap((dep) => dep.css || [])
        .map((css) => `  <link rel="stylesheet" href="${css}">`)
        .join('\n')
      const scripts = CDN_DEPS.map((dep) => `  <script src="${dep.url}"></script>`).join('\n')
      const inject = [styles, scripts].filter(Boolean).join('\n')
      return html.replace('<!-- CDN_INJECT -->', inject)
    },
  }
}

export default defineConfig(({ mode }) => {
  // 加载 .env / .env.[mode] 文件中的环境变量 (前缀不做限制, 全量加载)
  const env = loadEnv(mode, process.cwd(), '')
  // 是否启用前端 Mock 拦截 (VITE_USE_MOCK=true 时启用)
  const useMock = env.VITE_USE_MOCK === 'true'
  // 是否开启 bundle 体积分析 (ANALYZE=true 时启用)
  const analyze = env.ANALYZE === 'true'
  // P2-6: 是否启用 CDN 外置（仅生产环境启用，可通过 VITE_CDN_ENABLED=false 关闭）
  const cdnEnabled = mode === 'production' && env.VITE_CDN_ENABLED !== 'false'
  // 测试环境下禁用 Element Plus 样式自动导入，避免 vitest 无法处理 CSS 文件
  const isTest = process.env.VITEST === 'true'
  const epResolverOption = isTest ? { importStyle: false } : {}

  return {
    // hash 路由模式下使用相对路径, 以便部署到子路径; history 模式使用根路径
    base: env.VITE_ROUTER_MODE === 'hash' ? './' : '/',

    plugins: [
      // Vue SFC 编译插件
      vue(),
      // P2-6: 生产环境向 index.html 注入 CDN <script>/<link>（开发环境无副作用）
      injectCdn(cdnEnabled),
      // 自动导入 Vue / Vue Router / Pinia 的 API, 免去手动 import ref/reactive 等
      AutoImport({
        imports: ['vue', 'vue-router', 'pinia'],
        resolvers: [ElementPlusResolver(epResolverOption)],
        dts: 'auto-imports.d.ts',
        eslintrc: {
          enabled: true,
        },
      }),
      // 自动按需注册 Element Plus 组件, 配合 ts 声明文件提供类型提示
      // 测试模式下禁用，以便测试中可用 app.component 注册桩组件替代
      ...(isTest
        ? []
        : [
            Components({
              resolvers: [ElementPlusResolver(epResolverOption)],
              dts: 'components.d.ts',
            }),
          ]),
      // P1 批次 20: 独立开发 mock 插件
      // 启用后, 拦截 /* 请求直接返回 mock 数据
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
          // 排除 bundle 分析报告，避免 PWA 预缓存超限（stats.html 由 visualizer 生成，无需离线）
          globIgnores: ['**/stats.html'],
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
      // 图片/字体压缩优化：无损/有损压缩构建产物中的图片和 SVG 资源
      ...(mode === 'production'
        ? [
            viteImagemin({
              gifsicle: { optimizationLevel: 7, interlaced: false },
              optipng: { optimizationLevel: 7 },
              mozjpeg: { quality: 80 },
              pngquant: { quality: [0.8, 0.9], speed: 4 },
              svgo: { plugins: [{ name: 'removeViewBox', active: false }] },
              webp: { quality: 80 },
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
      // 当 mock 关闭时, 代理 /* 到后端
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
      // 生产环境生成 hidden sourcemap（不引用到 HTML 中，避免暴露源码）
      // 配合 CI/CD 中 sentry-cli 上传 sourcemap 到 Sentry，实现错误堆栈还原
      // 开发环境 sourcemap 由 Vite 默认开启
      sourcemap: mode === 'production' ? 'hidden' : true,
      target: 'es2020', // 编译目标: ES2020 (可选链 / 空值合并等原生支持)
      minify: 'esbuild', // 使用 esbuild 压缩, 速度优于 terser
      chunkSizeWarningLimit: 1500, // chunk 体积告警阈值 (kB), 兼容 vxe-table 等大依赖
      rollupOptions: {
        // P2-6: 生产环境将 CDN 依赖标记为 external，不打包进 bundle
        external: cdnEnabled ? CDN_DEPS.map((dep) => dep.name) : [],
        output: {
          // P2-6: external 依赖对应的全局变量名映射（UMD/IIFE 全局变量名）
          globals: cdnEnabled
            ? Object.fromEntries(CDN_DEPS.map((dep) => [dep.name, dep.var]))
            : {},
          // 手动拆分 vendor chunk, 提升缓存命中率并减少首屏体积
          // 使用函数形式精确匹配模块路径,避免对象形式引用全量包
          // 注意：被 external 的依赖不会进入 manualChunks 判断（已不参与打包）
          manualChunks(id) {
            // Vite 会将路径规范化为正斜杠,但为兼容性同时处理 Windows 反斜杠
            const normalizedId = id.replace(/\\/g, '/')

            // ECharts 按需引入:仅匹配 echarts/ 和 zrender/ 子模块,不匹配全量 'echarts' 包入口
            // 配合 src/utils/echarts.ts 集中注册,相比全量引入体积减少约 60%
            if (normalizedId.includes('node_modules/echarts/') || normalizedId.includes('node_modules/zrender/')) {
              return 'echarts'
            }
            // Vue 全家桶
            if (
              normalizedId.includes('node_modules/vue/') ||
              normalizedId.includes('node_modules/vue-router/') ||
              normalizedId.includes('node_modules/pinia/')
            ) {
              return 'vue'
            }
            // Element Plus UI 库
            if (
              normalizedId.includes('node_modules/element-plus/') ||
              normalizedId.includes('node_modules/@element-plus/icons-vue/')
            ) {
              return 'element'
            }
            // 高性能表格
            if (
              normalizedId.includes('node_modules/vxe-table/') ||
              normalizedId.includes('node_modules/xe-utils/')
            ) {
              return 'vxe-table'
            }
            // BPMN 流程图
            if (normalizedId.includes('node_modules/bpmn-js/')) {
              return 'bpmn'
            }
            // 代码编辑器
            if (
              normalizedId.includes('node_modules/codemirror/') ||
              normalizedId.includes('node_modules/@codemirror/')
            ) {
              return 'codemirror'
            }
            return undefined
          },
        },
      },
    },

    define: {
      // 注入应用版本号, 业务代码可通过 __VITE_APP_VERSION__ 读取 (取自 package.json)
      __VITE_APP_VERSION__: JSON.stringify(process.env.npm_package_version),
    },

    // Vitest 配置：排除 e2e 目录（由 Playwright 运行），使用 jsdom 环境
    test: {
      environment: 'jsdom',
      exclude: ['node_modules', 'dist', 'e2e'],
      // 覆盖率配置（CI 门禁: lines ≥ 60%, functions ≥ 50%, branches ≥ 50%）
      coverage: {
        provider: 'v8',
        reporter: ['text', 'text-summary', 'lcov', 'html'],
        reportsDirectory: './coverage',
        // 排除非业务逻辑文件
        exclude: [
          'node_modules/**',
          'dist/**',
          'e2e/**',
          'src/**/*.d.ts',
          'src/types/**',
          'src/mock/**',
          'src/config/**',
          'src/plugins/**',
          'src/main.ts',
          'src/App.vue',
          'src/vite-env.d.ts',
          'auto-imports.d.ts',
          'components.d.ts',
        ],
        // 覆盖率门禁阈值（分阶段提升）
        thresholds: {
          lines: 60,
          functions: 50,
          branches: 50,
        },
      },
    },
  }
})
