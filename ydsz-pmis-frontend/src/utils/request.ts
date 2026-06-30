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
 * 统一响应格式
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp?: number
}

/**
 * 分页响应
 */
export interface PageResult<T = unknown> {
  list: T[]
  total: number
  page: number
  size: number
  pages: number
}

const service: AxiosInstance = axios.create({
  baseURL: `${import.meta.env.VITE_API_BASE_URL}${import.meta.env.VITE_API_PREFIX}`,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json;charset=UTF-8' },
})

// 请求拦截器
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

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const res = response.data

    // 二进制流直接返回
    if (response.config.responseType === 'blob') {
      return response as unknown as ApiResponse
    }

    if (res.code === 0 || res.code === 200) {
      return res as unknown as ApiResponse
    }

    // 401: Token 失效
    if (res.code === 20001 || res.code === 20002 || res.code === 20003) {
      handleUnauthorized(res.message)
      return Promise.reject(new Error(res.message))
    }

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

function handleUnauthorized(message?: string): void {
  ElMessage.error(message || '登录已过期，请重新登录')
  const userStore = useUserStore()
  userStore.clearAuth()
  const redirect = encodeURIComponent(window.location.hash.slice(1) || '/')
  window.location.href = `/#/login?redirect=${redirect}`
}

export function request<T = unknown>(config: AxiosRequestConfig): Promise<ApiResponse<T>> {
  return service(config) as unknown as Promise<ApiResponse<T>>
}

export default service
