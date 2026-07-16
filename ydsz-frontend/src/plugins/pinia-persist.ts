/**
 * @fileoverview Pinia 持久化插件配置
 * @description 基于 pinia-plugin-persistedstate v4 的统一持久化方案：
 * - 默认存储: localStorage
 * - Key 格式: `pmis:${storeName}:v1`（含版本号，便于后续 schema 迁移）
 * - 默认不持久化：仅当 store 在选项中显式声明 `persist` 时才开启
 * - 敏感数据混淆：序列化后整体 Base64 编码（轻量混淆，非加密）
 * @module plugins/pinia-persist
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Pinia } from 'pinia'
import { createPersistedState } from 'pinia-plugin-persistedstate'

/** 持久化 schema 版本号（升级时递增，配合 key 后缀做版本迁移） */
const PERSIST_VERSION = 'v1'
/** 持久化 key 前缀，统一标识 PMIS 持久化数据 */
const PERSIST_PREFIX = 'pmis'

/**
 * 将字符串编码为 Base64（兼容 Unicode，避免 btoa 在遇到中文等字符时报 "Invalid character"）
 * @param str 原始字符串
 * @returns Base64 编码字符串
 */
function encodeBase64(str: string): string {
  const bytes = new TextEncoder().encode(str)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

/**
 * 将 Base64 字符串解码为原始字符串（兼容 Unicode）
 * @param b64 Base64 编码字符串
 * @returns 原始字符串
 */
function decodeBase64(b64: string): string {
  const binary = atob(b64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return new TextDecoder().decode(bytes)
}

/**
 * 安装 Pinia 持久化插件
 *
 * 在创建 pinia 实例后、`app.use(pinia)` 之前调用：
 *
 * ```ts
 * import pinia from '@/store'
 * import { setupPiniaPersist } from '@/plugins/pinia-persist'
 * setupPiniaPersist(pinia)
 * app.use(pinia)
 * ```
 *
 * 默认策略：
 *  - 仅持久化在 store 选项中显式声明 `persist` 的 store
 *  - 敏感字段（token、permissions 等）随整体 Base64 编码进行轻量混淆
 *
 * @param pinia Pinia 实例
 */
export function setupPiniaPersist(pinia: Pinia): void {
  pinia.use(
    createPersistedState({
      storage: localStorage,
      key: (storeId) => `${PERSIST_PREFIX}:${storeId}:${PERSIST_VERSION}`,
      serializer: {
        // 整体 Base64 编码：对持久化状态做轻量混淆，敏感字段（token/permissions）随之被编码。
        // 注意：Base64 并非加密，仅防止明文直接暴露在 localStorage 中。
        serialize: (value) => encodeBase64(JSON.stringify(value)),
        deserialize: (value) => JSON.parse(decodeBase64(value)),
      },
    }),
  )
}
