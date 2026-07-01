/**
 * @file Axios HTTP 请求封装
 * @description 统一封装 baseURL、Token 注入、TraceId 注入、错误处理、401 自动跳登录
 * @module utils/request
 *
 * 拦截器链路：
 *  - 请求拦截器：注入 Authorization Bearer Token + X-Trace-Id
 *  - 响应拦截器：
 *    - blob 直接返回（文件下载场景）
 *    - code 0/200 视为成功，返回 ApiResponse
 *    - code 20001/20002/20003 视为 Token 失效，跳登录
 *    - HTTP 401 跳登录；其他错误 ElMessage 提示
 */
import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getToken } from './auth'
import { generateTraceId } from './trace'

/**
 * 统一响应格式（与后端 com.njydsz.pmis.common.api.R<T> 对应）
 */
export interface ApiResponse<T = unknown> {
  /** 业务码：0/200 成功 */
  code: number
  /** 提示消息 */
  message: string
  /** 业务数据 */
  data: T
  /** 链路追踪 ID */
  traceId?: string
  /** 后端响应时间戳 */
  timestamp?: number
}

/**
 * 分页响应（旧版字段命名 list/total，新代码请使用 PageData records/total）
 */
export interface PageResult<T = unknown> {
  list: T[]
  total: number
  page: number
  size: number
  pages: number
}

/** Axios 实例（统一 baseURL、超时、Content-Type） */
const service: AxiosInstance = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL}${import.meta.env.VITE_API_PREFIX}`,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
})

// 请求拦截器：注入 Token + TraceId
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['X-Trace-Id'] = generateTraceId()
    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器：统一处理业务码与 HTTP 错误
service.interceptors.response.use(
  (response: AxiosResponse): any => {
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
      return Promise.reject(new Error(res.message))
    }

    // 其他业务错误：统一弹错
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message

    if (status === 401) {
      handleUnauthorized(message)
    } else {
      ElMessage.error(message || '网络异常')
    }
    return Promise.reject(error)
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
 * @param config - Axios 请求配置
 * @returns 解包后的 ApiResponse<T>，业务层可直接读取 data 字段
 */
export function request<T = unknown>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service(config) as unknown as Promise<ApiResponse<T>>
}

/** 导出 service 实例, 供测试与外部拦截器使用 */
export { service }

export default service
