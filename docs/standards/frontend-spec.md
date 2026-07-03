<!--
  ===========================================================================
  文件名: frontend-spec.md
  路径:   docs/standards/frontend-spec.md
  作用:   PMIS 前端工程（Vue 3 + Vite + TS）目录结构、组件、状态、路由、请求层规范
  技术栈: Vue 3.4+ / Vite 5+ / TypeScript 5+ / Pinia 2+ / Element Plus 2.7+ / vxe-table 4+
  对标:   Vue 官方风格指南 / 字节前端规范 / 阿里前端规约
  ===========================================================================
-->

# 前端工程规范

> 文档版本: V1.0 | 编制日期: 2026-06-30 | 最近更新: 2026-07-03
> 技术栈: Vue 3.4+ / Vite 5+ / TypeScript 5+ / Pinia 2+ / Element Plus 2.7+ / vxe-table 4+

> 📌 本规范适用于 `ydsz-pmis-frontend` 全栈代码，**所有 PR 必须先通过 ESLint + Prettier + TypeScript 类型检查**。

## 1. 目录结构

```
ydsz-pmis-frontend/
├── public/                          # 静态资源
├── src/
│   ├── api/                         # 接口请求层 (按业务模块)
│   │   ├── user/
│   │   │   ├── index.ts             # 接口方法
│   │   │   └── types.ts             # 接口类型
│   │   └── project/
│   ├── assets/                      # 静态资源 (图片/字体)
│   ├── components/                  # 通用组件
│   │   ├── common/                  # 通用基础组件 (Button/Table/Form)
│   │   └── business/                # 业务封装组件
│   ├── composables/                 # 组合式函数 (useXxx)
│   ├── config/                      # 全局配置
│   │   ├── env.ts                   # 环境变量
│   │   └── constants.ts             # 全局常量
│   ├── directives/                  # 自定义指令
│   ├── hooks/                       # 通用 Hook
│   ├── layout/                      # 布局组件
│   │   ├── default/                 # 默认布局
│   │   └── components/              # 布局子组件
│   ├── locales/                     # 国际化
│   ├── plugins/                     # 插件注册
│   ├── router/                      # 路由
│   │   ├── index.ts
│   │   ├── routes.ts                # 静态路由
│   │   └── guard.ts                 # 路由守卫
│   ├── store/                       # Pinia 状态
│   │   ├── modules/                 # 按业务模块拆分
│   │   └── index.ts
│   ├── styles/                      # 全局样式
│   │   ├── index.scss               # 入口
│   │   ├── variables.scss           # SCSS 变量
│   │   ├── mixins.scss              # SCSS 混入
│   │   └── reset.scss               # 样式重置
│   ├── types/                       # 全局类型
│   │   ├── api.ts                   # API 通用类型
│   │   ├── router.ts
│   │   └── global.d.ts
│   ├── utils/                       # 工具函数
│   │   ├── request.ts               # Axios 封装
│   │   ├── auth.ts                  # Token 工具
│   │   ├── format.ts                # 格式化
│   │   └── validate.ts              # 校验
│   ├── views/                       # 页面 (按业务模块)
│   │   ├── dashboard/               # 仪表盘
│   │   ├── user/                    # 用户管理
│   │   └── project/                 # 项目管理
│   ├── App.vue
│   ├── main.ts
│   └── vite-env.d.ts
├── .env.development                 # 开发环境变量
├── .env.production                  # 生产环境变量
├── .eslintrc.cjs                    # ESLint 配置
├── .prettierrc.json                 # Prettier 配置
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── README.md
```

## 2. 编码规范

### 2.1 组件编写

- **必须** 使用 `<script setup lang="ts">` 组合式 API
- **必须** 显式声明 props 类型与默认值
- **必须** 显式声明 emits
- 单文件组件行数 ≤ 300 行，超出需拆分

```vue
<script setup lang="ts">
import { ref } from 'vue'
import type { UserVO } from '@/api/user/types'

interface Props {
  user: UserVO
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false
})

const emit = defineEmits<{
  (e: 'submit', data: UserVO): void
  (e: 'cancel'): void
}>()

const formRef = ref()

const handleSubmit = () => {
  // ...
  emit('submit', props.user)
}
</script>

<template>
  <el-form ref="formRef" :model="user">
    <el-form-item label="姓名">
      <el-input v-model="user.name" :disabled="readonly" />
    </el-form-item>
    <el-button type="primary" @click="handleSubmit">提交</el-button>
  </el-form>
</template>
```

### 2.2 命名

| 类别 | 规则 | 示例 |
|------|------|------|
| 组件文件名 | 大驼峰 | `UserCard.vue` |
| 组件名 | 大驼峰 | `<UserCard />` |
| 事件处理函数 | handle + 动作 | `handleSubmit`, `handleClick` |
| 异步加载方法 | fetch + 数据 | `fetchUserList` |
| 布尔 props | is/has/can/should 前缀 | `isLoading`, `hasPermission` |
| 布尔状态变量 | is/has 前缀 | `isShowDialog`, `hasMore` |

### 2.3 状态管理

- 全局共享状态使用 Pinia
- 单个 store 文件 ≤ 200 行
- 异步 action 必须使用 `try/catch` + 错误状态
- 禁止在 store 中直接调用 UI 组件

```typescript
// store/modules/user.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loginApi, getUserInfoApi } from '@/api/user'
import type { UserInfo, LoginParams } from '@/api/user/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)
  const isLoggedIn = computed(() => !!token.value)

  async function login(params: LoginParams) {
    const { data } = await loginApi(params)
    token.value = data.token
  }

  async function fetchUserInfo() {
    const { data } = await getUserInfoApi()
    userInfo.value = data
  }

  function logout() {
    token.value = ''
    userInfo.value = null
  }

  return { token, userInfo, isLoggedIn, login, fetchUserInfo, logout }
})
```

## 3. 路由规范

### 3.1 静态路由

```typescript
// router/routes.ts
import type { RouteRecordRaw } from 'vue-router'

export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/',
    component: () => import('@/layout/default/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'dashboard' }
      }
    ]
  }
]
```

### 3.2 动态路由

通过权限接口获取菜单树，动态注册路由：

```typescript
// router/guard.ts
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  // 已登录
  if (userStore.token) {
    if (to.path === '/login') {
      next({ path: '/' })
    } else {
      if (!userStore.userInfo) {
        await userStore.fetchUserInfo()
      }
      next()
    }
  } else {
    if (to.meta.whiteList) {
      next()
    } else {
      next(`/login?redirect=${to.path}`)
    }
  }
})
```

## 4. 接口请求层

### 4.1 Axios 封装

```typescript
// utils/request.ts
import axios, { type AxiosInstance, type AxiosRequestConfig } from 'axios'
import { useUserStore } from '@/store/modules/user'
import { ElMessage } from 'element-plus'

const service: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    config.headers['X-Trace-Id'] = generateTraceId()
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const { code, data, message } = response.data
    if (code === 0) {
      return data
    }
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error) => {
    ElMessage.error(error.response?.data?.message || '网络异常')
    return Promise.reject(error)
  }
)

export function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  return service(config) as unknown as Promise<T>
}
```

### 4.2 接口定义

```typescript
// api/user/index.ts
import { request } from '@/utils/request'
import type { LoginParams, LoginResult, UserInfo } from './types'

export const loginApi = (data: LoginParams) =>
  request<LoginResult>({ url: '/api/v1/auth/login', method: 'POST', data })

export const getUserInfoApi = () =>
  request<UserInfo>({ url: '/api/v1/users/me', method: 'GET' })

export const logoutApi = () =>
  request<void>({ url: '/api/v1/auth/logout', method: 'POST' })
```

## 5. 性能规范

- 路由懒加载：`() => import('@/views/...')`
- 大表格虚拟滚动：vxe-table 默认开启
- 图片懒加载：`<el-image lazy />`
- 列表分页：服务端分页，禁止前端全量渲染
- 防抖/节流：搜索框 300ms 防抖
- 长列表 key：使用业务主键，禁止使用 index

## 6. 样式规范

- 全局样式仅在 `styles/index.scss` 引入
- 组件样式使用 `<style scoped lang="scss">`
- 单位：rpx 仅在移动端使用，PC 端使用 px / em / rem
- 颜色：使用 SCSS 变量，禁止硬编码色值
- 命名：BEM（`block__element--modifier`）

## 7. ESLint + Prettier 强制

```json
// .eslintrc.cjs
module.exports = {
  root: true,
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 2022,
    sourceType: 'module'
  },
  extends: [
    'plugin:vue/vue3-recommended',
    'plugin:@typescript-eslint/recommended',
    'prettier'
  ],
  rules: {
    'vue/multi-word-component-names': 'off',
    '@typescript-eslint/no-unused-vars': 'error',
    'no-console': ['warn', { allow: ['warn', 'error'] }],
    'no-debugger': 'error',
    'eqeqeq': ['error', 'always'],
    'no-var': 'error'
  }
}
```

## 8. 提交前检查

- `pnpm lint` (ESLint + Prettier)
- `pnpm type-check` (TS 类型检查)
- `pnpm test` (Vitest 单元测试)
- Husky + lint-staged 在 commit 时自动执行

## 9. 可访问性（A11Y）

- 所有交互元素必须有 `aria-label` 或可见文本
- 颜色对比度 ≥ 4.5:1（WCAG 2.1 AA）
- 键盘导航：Tab 顺序合理，焦点可见
- 表单错误提示使用 `aria-describedby` 关联

## 10. 国际化（i18n）

- 使用 `vue-i18n` 9.x + Composition API
- 文案统一放在 `src/locales/{zh-CN,en-US}/**/*.ts`
- **禁止** 在模板/JS 中硬编码中文字符串
- 动态菜单、权限码等也需要支持国际化

## 11. 错误监控

- 已集成 Sentry（`@sentry/vue`）
- 全局异常通过 `app.config.errorHandler` 捕获
- 关键业务操作（登录、提交、支付）增加 Sentry Breadcrumb

## 12. 性能预算

| 资源 | 预算 | 监控 |
|------|------|------|
| 首屏 JS | ≤ 500KB（gzip） | `rollup-plugin-visualizer` |
| 首屏 CSS | ≤ 100KB（gzip） | 同上 |
| 首屏图片 | ≤ 300KB | `<el-image lazy />` |
| LCP | ≤ 2.5s | Web Vitals |
| FID | ≤ 100ms | Web Vitals |
| CLS | ≤ 0.1 | Web Vitals |

## 13. 浏览器兼容

| 浏览器 | 最低版本 |
|--------|----------|
| Chrome | 100+ |
| Edge | 100+ |
| Firefox | 100+ |
| Safari | 15+ |
| ❌ IE | 不支持 |

## 14. 变更记录

| 日期 | 版本 | 变更人 | 变更内容 |
|------|------|--------|----------|
| 2026-07-03 | 1.1 | 前端架构组 | 新增 §9 A11Y、§10 i18n、§11 错误监控、§12 性能预算、§13 浏览器兼容 |
| 2026-06-30 | 1.0 | 前端架构组 | 初始版本 |

