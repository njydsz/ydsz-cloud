/**
 * @file Axios HTTP 请求封装
 * @description 统一封装 baseURL、Token 注入、TraceId 注入、全局 loading、错误处理、401 自动跳登录
 * @module utils/request
 *
 * 拦截器链路：
 *  - 请求拦截器：注入 Authorization Bearer Token + X-Trace-Id + 全局 loading 开启
 *  - 响应拦截器：
 *    - blob 直接返回（文件下载场景）
 *    - code 0/200 视为成功，返回 ApiResponse
 *    - code 20001/20002/20003 视为 Token 失效，跳登录
 *    - HTTP 401 跳登录；其他错误 ElMessage 提示
 *    - 全局 loading 关闭
 *
 * P1-7 改进：
 *  - 全局 loading 服务（并发计数，支持 silent 跳过）
 *  - 错误去重：拦截器弹错后抛 BizException/HttpException（handled=true），业务层可用 isHandledError() 判断
 *  - 类型合并：ApiResponse/PageData 统一从 @/types/api 导入
 */
import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import { ElMessage, ElLoading } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getToken } from './auth'
import { generateTraceId } from './trace'
import { BizException, HttpException } from './error'

// 类型统一从 @/types/api 导入，避免重复定义
export type { ApiResponse, PageData, PageQuery, PagedApiResponse } from '@/types/api'
import type { ApiResponse } from '@/types/api'

/**
 * 旧版分页响应别名（兼容已有代码，新代码请使用 PageData）
 * @deprecated 请使用 @/types/api 中的 PageData
 */
export type PageResult<T = unknown> = {
  list: T[]
  total: number
  page: number
  size: number
  pages: number
}

// ==================== 全局 Loading 服务 ====================

/** 全局 loading 扩展配置 */
declare module 'axios' {
  interface AxiosRequestConfig {
    /** 是否静默请求（不显示全局 loading），默认 false */
    silent?: boolean
  }
}

/** 当前活跃请求计数 */
let loadingCount = 0
/** 全局 loading 实例 */
let loadingInstance: { close: () => void } | null = null

/** 开启全局 loading（并发计数） */
function showLoading(): void {
  loadingCount++
  if (loadingCount === 1) {
    loadingInstance = ElLoading.service({
      lock: true,
      text: '加载中...',
      background: 'rgba(0, 0, 0, 0.05)',
    })
  }
}

/** 关闭全局 loading（并发计数归零时关闭） */
function hideLoading(): void {
  if (loadingCount > 0) {
    loadingCount--
  }
  if (loadingCount === 0 && loadingInstance) {
    loadingInstance.close()
    loadingInstance = null
  }
}

// ==================== P2-7: 自动重试配置 ====================

/** 最大重试次数（不含首次请求） */
const MAX_RETRIES = 2
/** 重试基础延迟（ms），指数退避: 1s → 2s */
const RETRY_BASE_DELAY = 1000

/**
 * 判断错误是否可重试
 * - 网络断开（!error.response）→ 可重试
 * - 请求超时（ECONNABORTED）→ 可重试
 * - 服务端 5xx 错误 → 可重试
 * - 4xx 客户端错误、401 未授权、业务错误 → 不可重试
 */
function isRetryableError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false
  const err = error as { code?: string; response?: { status?: number } }
  // 网络断开
  if (!err.response) return true
  // 超时
  if (err.code === 'ECONNABORTED') return true
  // 5xx 服务端错误
  const status = err.response?.status
  if (status && status >= 500 && status < 600) return true
  return false
}

/** 指数退避延迟计算 */
function getRetryDelay(retryCount: number): number {
  return RETRY_BASE_DELAY * Math.pow(2, retryCount)
}

// ==================== Axios 实例 ====================

/** Axios 实例（统一 baseURL、超时、Content-Type） */
const service: AxiosInstance = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL}${import.meta.env.VITE_API_PREFIX}`,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
})

// 请求拦截器：注入 Token + TraceId + 全局 loading
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['X-Trace-Id'] = generateTraceId()

    // 非静默请求开启全局 loading
    if (!config.silent) {
      showLoading()
    }
    return config
  },
  (error) => {
    // 请求发送失败也要关闭 loading
    hideLoading()
    return Promise.reject(error)
  },
)

// 响应拦截器：统一处理业务码与 HTTP 错误
service.interceptors.response.use(
  (response: AxiosResponse): any => {
    // 非静默请求关闭全局 loading
    if (!response.config.silent) {
      hideLoading()
    }

    const res = response.data as ApiResponse

    // 二进制流（文件下载）直接返回原始 response，便于上层读取 blob
    if (response.config.responseType === 'blob') {
      return response
    }

    // 业务成功
    if (res.code === 0 || res.code === 200) {
      return res
    }

    // Token 失效业务码：20001 未登录 / 20002 Token 过期 / 20003 Token 无效
    if (res.code === 20001 || res.code === 20002 || res.code === 20003) {
      handleUnauthorized(res.message)
      return Promise.reject(new BizException(res.message, res.code, true))
    }

    // 其他业务错误：统一弹错 + 抛 BizException（handled=true 标记拦截器已处理）
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new BizException(res.message || '请求失败', res.code, true))
  },
  (error) => {
    // 非静默请求关闭全局 loading
    if (!error.config?.silent) {
      hideLoading()
    }

    // P2-7: 网络错误/超时/5xx 自动重试（仅 GET 请求，避免非幂等操作重复提交）
    const retryCount = (error.config as any)?._retryCount || 0
    const isGet = error.config?.method?.toLowerCase() === 'get'
    if (isGet && retryCount < MAX_RETRIES && isRetryableError(error)) {
      ;(error.config as any)._retryCount = retryCount + 1
      const delay = getRetryDelay(retryCount)
      // eslint-disable-next-line no-console
      console.debug(`[request] 第 ${retryCount + 1} 次重试（${delay}ms 后）: ${error.config?.url}`)
      return new Promise((resolve) => setTimeout(resolve, delay))
        .then(() => service(error.config!))
    }

    const status = error.response?.status
    const message = error.response?.data?.message || error.message

    if (status === 401) {
      handleUnauthorized(message)
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else if (!error.response) {
      ElMessage.error('网络连接异常，请检查网络')
    } else {
      ElMessage.error(message || '网络异常')
    }
    return Promise.reject(new HttpException(message || '网络异常', status || 0, true))
  },
)

/**
 * 处理 401 未授权：弹错 + 清空登录态 + 跳转登录页
 * @param message - 错误提示文案
 */
function handleUnauthorized(message?: string): void {
  ElMessage.error(message || '登录已过期，请重新登录')
  const userStore = useUserStore()
  userStore.clearAuth()
  const redirect = encodeURIComponent(window.location.hash.slice(1) || '/')
  window.location.href = `/#/login?redirect=${redirect}`
}

/**
 * 通用请求方法（推荐使用）
 * @param config - Axios 请求配置，支持 silent 跳过全局 loading
 * @returns 解包后的 ApiResponse<T>，业务层可直接读取 data 字段
 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service(config) as unknown as Promise<ApiResponse<T>>
}

/** 导出 service 实例, 供测试与外部拦截器使用 */
export { service }

export default service
