/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string
  readonly VITE_API_BASE_URL: string
  readonly VITE_API_PREFIX: string
  readonly VITE_UPLOAD_URL: string
  readonly VITE_USE_MOCK: string
  readonly VITE_ROUTER_MODE: 'hash' | 'history'
  readonly VITE_TOKEN_KEY: string
  readonly VITE_REFRESH_TOKEN_KEY: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

/**
 * 全局通用类型 - 与后端响应保持一致
 */
interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: number
}

interface PageResult<T = unknown> {
  list: T[]
  total: number
  page: number
  size: number
  pages?: number
}
