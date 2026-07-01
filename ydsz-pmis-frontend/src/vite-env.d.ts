/**
 * @file Vite 环境变量与全局类型声明
 * @description 声明 import.meta.env 中可用的环境变量类型、.vue 模块类型，以及全局通用 API 类型
 * @module env
 *
 * 与 .env / .env.development / .env.production 配套使用，所有 VITE_ 前缀变量会被 Vite 静态替换。
 */

/// <reference types="vite/client" />

/** Vite 注入的环境变量类型定义 */
interface ImportMetaEnv {
  /** 应用标题（展示在浏览器标签页） */
  readonly VITE_APP_TITLE: string
  /** 后端 API 基础地址（如 https://api.pmis.example.com） */
  readonly VITE_API_BASE_URL: string
  /** API 路径前缀（如 /api/v1） */
  readonly VITE_API_PREFIX: string
  /** 文件上传地址 */
  readonly VITE_UPLOAD_URL: string
  /** 是否启用 Mock 服务（'true' / 'false'） */
  readonly VITE_USE_MOCK: string
  /** 路由模式：hash 适用于 GitHub Pages 等静态托管，history 适用于常规部署 */
  readonly VITE_ROUTER_MODE: 'hash' | 'history'
  /** Access Token 在 localStorage 中的 key */
  readonly VITE_TOKEN_KEY: string
  /** Refresh Token 在 localStorage 中的 key */
  readonly VITE_REFRESH_TOKEN_KEY: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

/** .vue 单文件组件模块类型声明，使 TypeScript 能识别 import Xxx from './Xxx.vue' 语法 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

/**
 * 全局通用类型 - 与后端响应保持一致
 *
 * @deprecated 业务代码请优先从 @/types/api 或 @/utils/request 显式导入 ApiResponse/PageData，
 *             全局声明仅为兼容旧代码与第三方库类型推断而保留。
 */
interface ApiResponse<T = unknown> {
  /** 业务码：0/200 成功，401 未授权，其他为业务错误 */
  code: number
  /** 提示消息 */
  message: string
  /** 业务数据 */
  data: T
  /** 链路追踪 ID（与后端 X-Trace-Id 对应） */
  traceId?: string
  /** 后端响应时间戳（毫秒） */
  timestamp?: number
}

/** 分页结果（旧版字段命名 list/total，新代码请使用 PageData records/total） */
interface PageResult<T = unknown> {
  list: T[]
  total: number
  page: number
  size: number
  pages?: number
}
