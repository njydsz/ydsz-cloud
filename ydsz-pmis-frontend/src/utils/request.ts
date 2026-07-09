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
 *    - code 20001/20002/20003 / HTTP 401 视为 Token 失效，尝试 refreshToken 无感续期并重试原始请求
 *    - 并发 401 通过 Promise 队列合并为一次刷新请求，刷新成功后统一重试
 *    - 续期失败（refreshToken 也过期）才清空登录态并跳转登录页
 *    - 其他错误 ElMessage 提示
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
  CanceledError,
} from 'axios'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/store/modules/user'
import { getToken, getRefreshToken, setToken } from './auth'
import { generateTraceId } from './trace'
import { BizException, HttpException } from './error'
import { requestCanceler } from './request-canceler'
import i18n from '@/locales'
import { logger } from '@/utils/logger'

/**
 * 解析后端返回的错误消息：如果消息是 i18n key（以 "error." 开头），
 * 通过 vue-i18n 翻译为当前语言；否则原样返回。
 */
function resolveErrorMessage(msg?: string): string {
  if (!msg) return i18n.global.t('request.requestFailed')
  if (msg.startsWith('error.')) {
    // 后端返回的 i18n key，如 "error.UNAUTHORIZED"
    const key = msg.substring(6) // 去掉 "error." 前缀
    return i18n.global.t(`error.${key}`) || msg
  }
  if (msg.startsWith('validation.')) {
    return i18n.global.t(msg) || msg
  }
  return msg
}

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
    /** 重试计数（内部使用，用于 GET 请求自动重试） */
    _retryCount?: number
    /** 标记为刷新 Token 请求，跳过响应拦截器的无感刷新逻辑，避免递归 */
    _isRefreshTokenRequest?: boolean
    /** 标记请求是否已因 Token 过期重试过，防止刷新后仍 401 造成死循环 */
    _tokenRefreshed?: boolean
    /** 请求元数据（内部使用，记录请求开始时间用于性能监控） */
    metadata?: { startTime: number }
    /**
     * 是否跳过请求取消管理（不纳入 pending Map，不受 cancelAll / cancelByUrl 影响）
     *
     * 适用于：路由守卫自身的请求（getUserInfo）、轮询、WebSocket 相关等不应被取消的请求
     */
    skipCancel?: boolean
  }
}

/** 当前活跃请求计数 */
let loadingCount = 0

// NProgress 配置：顶部细条进度条，替代全屏 Loading 遮罩，提升用户体验
NProgress.configure({
  showSpinner: false, // 不显示右上角旋转图标
  minimum: 0.15, // 起始进度 15%
  trickleSpeed: 200, // 每 200ms 自动递增
  easing: 'ease', // 动画缓动
  speed: 300, // 完成动画速度
})

/** 开启顶部进度条（并发计数，第一个请求启动 NProgress） */
function showLoading(): void {
  loadingCount++
  if (loadingCount === 1) {
    NProgress.start()
  }
}

/** 关闭顶部进度条（并发计数归零时 done） */
function hideLoading(): void {
  if (loadingCount > 0) {
    loadingCount--
  }
  if (loadingCount === 0) {
    NProgress.done()
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
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8',
    // CSRF 双重防护：X-Requested-With 使请求变为非简单请求，触发 CORS 预检，
    // 阻止跨域简单请求绕过预检的 CSRF 攻击向量
    'X-Requested-With': 'XMLHttpRequest',
  },
})

// 请求拦截器：注入 Token + TraceId + 全局 loading + 性能监控 + 请求取消管理
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    config.headers['X-Trace-Id'] = generateTraceId()
    // P1: 注入 Accept-Language 头，与后端 i18n MessageSource 对齐
    // vue-i18n legacy 模式下 locale 是 ref<string>；composition 模式下是 Ref<string>
    const locale = (i18n.global.locale as unknown as { value?: string })?.value
      || (i18n.global.locale as unknown as string)
      || 'zh-CN'
    config.headers['Accept-Language'] = locale

    // 记录请求开始时间（用于性能监控）
    config.metadata = { startTime: performance.now() }

    // 纳入请求取消管理（写入 config.signal，重复请求自动取消旧请求）
    // skipCancel: true 的请求不纳入管理
    requestCanceler.addPending(config)

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

// 响应拦截器：统一处理业务码与 HTTP 错误 + 性能监控
service.interceptors.response.use(
  (response: AxiosResponse): AxiosResponse | Promise<AxiosResponse> => {
    // 请求完成：从 pending Map 中移除
    requestCanceler.removePending(response.config)

    // 非静默请求关闭全局 loading
    if (!response.config.silent) {
      hideLoading()
    }

    // 计算请求耗时并记录慢请求
    const startTime = response.config.metadata?.startTime
    if (startTime) {
      const duration = performance.now() - startTime
      if (duration > 3000) {
        logger.warn('[SlowRequest]', `${response.config.url} took ${duration.toFixed(0)}ms`)
      }
    }

    const res = response.data as ApiResponse<unknown>

    // 二进制流（文件下载）直接返回原始 response，便于上层读取 blob
    if (response.config.responseType === 'blob') {
      return response
    }

    // 业务成功
    if (res.code === 0 || res.code === 200) {
      return res as unknown as AxiosResponse
    }

    // 刷新 Token 请求自身返回业务错误：静默拒绝（handled=false），
    // 由调用方 doRefreshToken 的 catch 统一处理跳转，避免重复弹错
    if (response.config._isRefreshTokenRequest) {
      return Promise.reject(new BizException(resolveErrorMessage(res.message) || i18n.global.t('request.refreshFailed'), res.code, false))
    }

    // Token 失效业务码：20001 未登录 / 20002 Token 过期 / 20003 Token 无效
    // 不直接跳登录，尝试使用 refreshToken 无感续期并重试原始请求
    if (res.code === 20001 || res.code === 20002 || res.code === 20003) {
      return handleTokenExpired(response.config, res.message) as unknown as Promise<AxiosResponse>
    }

    // 其他业务错误：统一弹错 + 抛 BizException（handled=true 标记拦截器已处理）
    const errMsg = resolveErrorMessage(res.message)
    ElMessage.error(errMsg)
    return Promise.reject(new BizException(errMsg, res.code, true))
  },
  (error) => {
    // 请求结束（含失败）：从 pending Map 中移除
    if (error.config) {
      requestCanceler.removePending(error.config)
    }

    // 非静默请求关闭全局 loading
    if (!error.config?.silent) {
      hideLoading()
    }

    // 请求被主动取消（路由切换 / 组件卸载 / 重复请求）：静默处理，不弹 ElMessage
    // 直接 reject 原 CanceledError，调用方可用 axios.isCancel(error) 判断
    if (error instanceof CanceledError || axios.isCancel(error)) {
      return Promise.reject(error)
    }

    // 刷新 Token 请求自身失败：静默拒绝（handled=false），由调用方处理跳转
    if (error.config?._isRefreshTokenRequest) {
      const refreshMessage = error.response?.data?.message || error.message
      return Promise.reject(
        new HttpException(resolveErrorMessage(refreshMessage) || i18n.global.t('request.refreshFailed'), error.response?.status || 0, false),
      )
    }

    // P2-7: 网络错误/超时/5xx 自动重试（仅 GET 请求，避免非幂等操作重复提交）
    const retryCount = error.config?._retryCount || 0
    const isGet = error.config?.method?.toLowerCase() === 'get'
    if (isGet && retryCount < MAX_RETRIES && isRetryableError(error)) {
      if (error.config) error.config._retryCount = retryCount + 1
      const delay = getRetryDelay(retryCount)
      logger.debug('[request]', `第 ${retryCount + 1} 次重试（${delay}ms 后）: ${error.config?.url}`)
      return new Promise((resolve) => setTimeout(resolve, delay))
        .then(() => service(error.config!))
    }

    const status = error.response?.status
    const message = error.response?.data?.message || error.message

    if (status === 401) {
      // Access Token 失效，尝试无感续期；config 缺失时直接跳登录
      if (error.config) {
        return handleTokenExpired(error.config, message)
      }
      handleUnauthorized(message)
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error(i18n.global.t('request.timeout'))
    } else if (!error.response) {
      ElMessage.error(i18n.global.t('request.networkError'))
    } else if (status === 403) {
      // H16.5 修复：403 无权限差异化提示（后端未返回 message 时用 i18n 兜底）
      ElMessage.error(resolveErrorMessage(message) || i18n.global.t('request.forbidden'))
    } else if (status === 404) {
      // H16.5 修复：404 资源不存在差异化提示
      ElMessage.error(resolveErrorMessage(message) || i18n.global.t('request.notFound'))
    } else if (status && status >= 500 && status < 600) {
      // H16.5 修复：5xx 服务端异常差异化提示（重试耗尽后仍失败才走到这里）
      ElMessage.error(resolveErrorMessage(message) || i18n.global.t('request.serverError'))
    } else if (status === 400) {
      // H16.5 修复：400 参数错误差异化提示
      ElMessage.error(resolveErrorMessage(message) || i18n.global.t('request.badRequest'))
    } else {
      ElMessage.error(resolveErrorMessage(message) || i18n.global.t('request.networkAbnormal'))
    }
    return Promise.reject(new HttpException(resolveErrorMessage(message) || i18n.global.t('request.networkAbnormal'), status || 0, true))
  },
)

// ==================== Token 无感刷新 ====================

/** 是否正在刷新 Token（防止并发刷新，多个 401 合并为一次 refresh 请求） */
let isRefreshing = false

/** 刷新期间等待重试的请求队列 */
interface PendingRequest {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}
let pendingQueue: PendingRequest[] = []

/** 将请求加入等待队列，待刷新完成后统一重试 */
function addPendingRequest(): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    pendingQueue.push({ resolve, reject })
  })
}

/** 刷新成功：通知队列中所有请求使用新 token 重试 */
function flushPendingQueue(token: string): void {
  pendingQueue.forEach((item) => item.resolve(token))
  pendingQueue = []
}

/** 刷新失败：拒绝队列中所有请求 */
function rejectPendingQueue(error: unknown): void {
  pendingQueue.forEach((item) => item.reject(error))
  pendingQueue = []
}

/**
 * 调用 /auth/refresh 接口换取新的 accessToken
 *
 * 直接使用 service 实例发起请求，并标记 _isRefreshTokenRequest，
 * 使其跳过响应拦截器的无感刷新逻辑，避免递归调用。
 *
 * @returns 新的 accessToken
 */
async function doRefreshToken(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error(i18n.global.t('request.refreshTokenMissing'))
  }

  const res = (await service({
    url: '/auth/refresh',
    method: 'POST',
    params: { refreshToken },
    silent: true,
    _isRefreshTokenRequest: true,
    // 刷新 Token 请求不能被取消（路由切换时若被打断会导致用户被登出）
    skipCancel: true,
  })) as ApiResponse<{ accessToken?: string; refreshToken?: string; token?: string }>

  const newToken = res.data.accessToken || res.data.token || ''
  const newRefreshToken = res.data.refreshToken || refreshToken
  if (!newToken) {
    throw new Error(i18n.global.t('request.refreshTokenEmpty'))
  }

  // 持久化新 token（同时更新 localStorage 中的 accessToken / refreshToken）
  setToken(newToken, newRefreshToken)

  // 同步更新 user store（容错：store 未初始化时忽略）
  try {
    const userStore = useUserStore()
    userStore.token = newToken
    userStore.refreshToken = newRefreshToken
  } catch (_e) {
    // permission store 未初始化等场景忽略
  }

  // 通知 WebSocket 等 STOMP 连接使用新 Token 重建
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('token-refreshed', { detail: { token: newToken } }))
  }

  return newToken
}

/**
 * 处理 Token 过期：尝试刷新并重试原始请求
 *
 * 流程：
 *  1. 已重试过的请求再次 401 → 直接跳登录（防止死循环）
 *  2. 正在刷新中 → 加入队列等待，刷新完成后自动重试
 *  3. 未刷新 → 发起一次刷新，成功后重试自身并消费队列；失败则拒绝队列并跳登录
 *
 * @param config - 原始请求配置
 * @param message - 错误提示文案
 * @returns 重试后的响应 Promise
 */
function handleTokenExpired(config: AxiosRequestConfig, message?: string): Promise<unknown> {
  // 防止死循环：已刷新重试过的请求再次 401，直接跳登录
  if (config._tokenRefreshed) {
    handleUnauthorized(message)
    return Promise.reject(new BizException(resolveErrorMessage(message) || i18n.global.t('request.loginExpired'), 401, true))
  }
  config._tokenRefreshed = true

  // 已有刷新在进行中，排队等待
  if (isRefreshing) {
    return addPendingRequest().then(() => service(config))
  }

  // 无 refresh token，无法续期，直接跳登录
  if (!getRefreshToken()) {
    handleUnauthorized(message)
    return Promise.reject(new BizException(resolveErrorMessage(message) || i18n.global.t('request.loginExpired'), 401, true))
  }

  isRefreshing = true

  return doRefreshToken()
    .then((newToken) => {
      // 刷新成功：消费队列中的待重试请求
      flushPendingQueue(newToken)
      // 重试原始请求（请求拦截器会自动注入新 token）
      return service(config)
    })
    .catch((err) => {
      // 刷新失败：拒绝队列中所有请求并跳登录
      rejectPendingQueue(err)
      handleUnauthorized(i18n.global.t('request.loginExpiredRelogin'))
      return Promise.reject(new BizException(i18n.global.t('request.loginExpiredRelogin'), 401, true))
    })
    .finally(() => {
      isRefreshing = false
    })
}

/**
 * 处理 401 未授权：弹错 + 清空登录态 + 跳转登录页
 * @param message - 错误提示文案
 */
function handleUnauthorized(message?: string): void {
  ElMessage.error(resolveErrorMessage(message) || i18n.global.t('request.loginExpiredRelogin'))
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
