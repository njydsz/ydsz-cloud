/**
 * @file 应用全局入口
 * @description PMIS 运营管理系统前端启动文件
 * @module main
 *
 * 启动流程：
 *  1. 创建 Vue 应用实例
 *  2. 全局注册 Element Plus 图标组件（约 280 个）
 *  3. 注册自定义权限指令 v-permission
 *  4. 安装 Pinia 状态管理 & Vue Router
 *  5. 挂载至 #app
 *
 * 样式加载顺序：
 *  - Element Plus 基础样式 → 暗黑主题变量 → vxe-table 样式 → NProgress 进度条 → 全局 SCSS 覆盖
 */
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './store'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 样式（顺序敏感：基础框架样式先加载，业务 SCSS 后加载以覆盖默认值）
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'vxe-table/lib/style.css'
import 'nprogress/nprogress.css'
import '@/styles/index.scss'

// 权限指令
import { setupPermissionDirective } from './directives/permission'
// 图片懒加载指令
import { setupLazyDirective } from './directives/lazy'

const app = createApp(App)

// 全量注册 Element Plus 图标，模板中可直接使用 <el-icon><Edit /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as never)
}

// 注册权限指令 v-permission，用于按钮级权限控制
setupPermissionDirective(app)
// 注册图片懒加载指令 v-lazy，用于长列表图片性能优化
setupLazyDirective(app)

// 状态管理（必须先于 router 安装，路由守卫依赖 pinia store）
app.use(pinia)
// 路由（守卫内部会使用 userStore / permissionStore）
app.use(router)

// 挂载应用
app.mount('#app')

// P2-1: 生产环境启用 Sentry 错误监控 + 全局 Promise rejection 捕获
if (import.meta.env.PROD) {
  initSentry({
    dsn: import.meta.env.VITE_SENTRY_DSN || '',
    environment: import.meta.env.MODE,
    release: import.meta.env.VITE_APP_VERSION,
    tracesSampleRate: 0.1,
  }, app, router)

  // 捕获未处理的 Promise rejection
  window.addEventListener('unhandledrejection', (event) => {
    captureError(event.reason, { type: 'unhandledrejection' })
  })
}
