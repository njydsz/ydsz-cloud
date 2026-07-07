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
// Pinia 持久化插件（为显式声明 persist 的 store 提供 localStorage 持久化）
import { setupPiniaPersist } from './plugins/pinia-persist'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import i18n from './locales'
import formCreate from '@form-create/element-ui'

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
// Sentry 错误监控（生产环境动态加载）
import { initSentry, captureError } from './utils/sentry'
// 日志工具（开发环境使用）
import { logger } from './utils/logger'
// Web Vitals 性能监控
import { reportWebVitals } from './composables/usePerformance'
// P2-7: 大屏 rem 自适应（在应用挂载前初始化 html font-size，确保首屏即按视口宽度缩放）
import { initResponsive } from './composables/useResponsive'

const app = createApp(App)

/**
 * P0-E1: 全局错误处理器
 * 捕获以下未被子组件 ErrorBoundary 拦截的异常：
 *  - 组件事件处理器抛出的同步/异步错误
 *  - setup 中未被 onErrorCaptured 覆盖的同步错误
 *  - 生命周期钩子中的错误
 * 生产环境通过 Sentry 上报，开发环境输出到 console。
 */
app.config.errorHandler = (err, _instance, info) => {
  if (import.meta.env.PROD) {
    captureError(err, { componentTrace: info, source: 'app.errorHandler' })
  } else {
    logger.error('[app.errorHandler]', err, { info })
  }
}

// 全量注册 Element Plus 图标，模板中可直接使用 <el-icon><Edit /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as never)
}

/**
 * P2-5: ElDialog 全局无障碍访问默认配置
 *
 * 说明：
 *  - Element Plus 2.8.x 的 ElDialog 已内置 focus trap（通过 el-focus-trap 组件实现），
 *    打开时 Tab 键焦点不会跳出对话框，且默认已设置 aria-modal="true" 与 role="dialog"，
 *    因此无需额外引入 focus-trap 库。
 *  - 此处集中声明项目级 a11y 默认偏好：禁止点击遮罩关闭、允许 Esc 关闭、启用 focus trap。
 *  - 各业务对话框仍需通过 useModalA11y composable 补充焦点恢复能力（关闭后回到触发器）。
 */
app.config.globalProperties.$dialog = {
  closeOnClickModal: false,
  closeOnPressEscape: true,
  trapFocus: true,
}

// 注册权限指令 v-permission，用于按钮级权限控制
setupPermissionDirective(app)
// 注册图片懒加载指令 v-lazy，用于长列表图片性能优化
setupLazyDirective(app)

// 状态管理（必须先于 router 安装，路由守卫依赖 pinia store）
// P2-4: 注册持久化插件，必须在 app.use(pinia) 之前，确保 store 首次使用时已具备持久化能力
setupPiniaPersist(pinia)
app.use(pinia)
// 国际化（注册全局 $t 与 useI18n 组合式 API）
app.use(i18n)
// 动态表单引擎（form-create + Element Plus 适配）
app.use(formCreate)
// 路由（守卫内部会使用 userStore / permissionStore）
app.use(router)

// P2-7: 初始化 rem 自适应（设置 html font-size 并监听 resize，需在挂载前完成以保证首屏缩放正确）
initResponsive()

// 挂载应用
app.mount('#app')

// P0-E1: 全局 Promise rejection 捕获（生产环境 + 开发环境均启用）
// 生产环境：Sentry 上报
// 开发环境：console 输出，便于及时发现未处理的 Promise 异常
window.addEventListener('unhandledrejection', (event) => {
  const reason = event.reason
  if (import.meta.env.PROD) {
    captureError(reason, { type: 'unhandledrejection' })
  } else {
    logger.error('[unhandledrejection]', reason)
  }
})

// P2-1: 生产环境启用 Sentry 错误监控
if (import.meta.env.PROD) {
  initSentry({
    dsn: import.meta.env.VITE_SENTRY_DSN || '',
    environment: import.meta.env.MODE,
    release: import.meta.env.VITE_APP_VERSION,
    tracesSampleRate: 0.1,
    replaysSessionSampleRate: 0.1,
    replaysOnErrorSampleRate: 1.0,
  }, app, router)
}

// 启用 Web Vitals 性能监控
reportWebVitals()
