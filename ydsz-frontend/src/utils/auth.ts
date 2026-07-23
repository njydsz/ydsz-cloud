/**
 * @file Token 持久化工具
 * @description 封装 Access Token / Refresh Token 在 localStorage 的读写
 * @module utils/auth
 *
 * Key 来源：环境变量 VITE_TOKEN_KEY / VITE_REFRESH_TOKEN_KEY，未配置时使用默认值
 * 选择 localStorage 而非 cookie：避免 CSRF，但需配合后端 CORS 白名单
 */
/** Access Token 在 localStorage 中的 key */
const TOKEN_KEY = import.meta.env.VITE_TOKEN_KEY || 'ydsz_token'
/** Refresh Token 在 localStorage 中的 key */
const REFRESH_TOKEN_KEY = import.meta.env.VITE_REFRESH_TOKEN_KEY || 'ydsz_refresh_token'

/** 读取 Access Token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/**
 * 同时写入 Access Token 与可选的 Refresh Token
 * @param token - Access Token
 * @param refreshToken - Refresh Token（可选）
 */
export function setToken(token: string, refreshToken?: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  if (refreshToken) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  }
}

/** 读取 Refresh Token */
export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_TOKEN_KEY)
}

/** 清除 Access Token 与 Refresh Token */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}
