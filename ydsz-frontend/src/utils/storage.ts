/**
 * @file localStorage 安全访问工具
 * @description 封装 localStorage 的安全读写，防止隐私模式或存储被禁用时抛异常导致页面崩溃
 * @module utils/storage
 *
 * 使用场景：
 *  - 业务代码需要读写 localStorage 但不希望因存储不可用而崩溃
 *  - 清除 YDSZ 持久化数据（仅清除 `ydsz:` 前缀的 key）
 */

/** YDSZ 持久化数据 key 前缀，与 plugins/pinia-persist 保持一致（注意含冒号） */
const PERSIST_PREFIX = 'ydsz:'

/**
 * 安全读取 localStorage
 * @param key 存储 key
 * @returns 存储的字符串值；不存在或存储不可用时返回 null
 */
export function safeGet(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

/**
 * 安全写入 localStorage
 * @param key 存储 key
 * @param value 字符串值
 * @returns 写入成功返回 true；存储不可用或写入失败（如超出配额）返回 false
 */
export function safeSet(key: string, value: string): boolean {
  try {
    window.localStorage.setItem(key, value)
    return true
  } catch {
    return false
  }
}

/**
 * 安全删除 localStorage 中的指定 key
 * @param key 存储 key
 */
export function safeRemove(key: string): void {
  try {
    window.localStorage.removeItem(key)
  } catch {
    // 静默失败：存储不可用时无需抛异常
  }
}

/**
 * 清除所有 YDSZ 持久化数据
 *
 * 仅清除以 `ydsz:` 为前缀的 key（由 pinia-persist 插件写入的 store 数据），
 * 不影响其他业务 key（如 `ydsz_token`、`userInfo` 等历史 / 下划线 key）。
 */
export function clearAllPersisted(): void {
  try {
    const storage = window.localStorage
    const keysToRemove: string[] = []
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i)
      if (key && key.startsWith(PERSIST_PREFIX)) {
        keysToRemove.push(key)
      }
    }
    keysToRemove.forEach((key) => storage.removeItem(key))
  } catch {
    // 静默失败：存储不可用时无需抛异常
  }
}
