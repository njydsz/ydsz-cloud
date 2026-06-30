// 全局入口
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './store'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 样式
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'vxe-table/lib/style.css'
import 'nprogress/nprogress.css'
import '@/styles/index.scss'

// 权限指令
import { setupPermissionDirective } from './directives/permission'

const app = createApp(App)

// 注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component as never)
}

// 注册权限指令
setupPermissionDirective(app)

app.use(pinia)
app.use(router)

app.mount('#app')
