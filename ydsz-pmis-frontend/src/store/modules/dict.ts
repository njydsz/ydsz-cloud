/**
 * @file 字典缓存 Pinia Store
 * @description 管理后端数据字典的内存缓存，避免重复请求
 * @module store/modules/dict
 *
 * 使用方式：
 * ```ts
 * const dictStore = useDictStore()
 * await dictStore.loadDict('wbs_task_status')
 * const items = dictStore.getDictItems('wbs_task_status')
 * ```
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listDictItems } from '@/api/system/dict'
import type { DictItemVO } from '@/api/system/dict/types'

/** localStorage 缓存前缀 */
const STORAGE_PREFIX = 'pmis_dict_'
/** localStorage 缓存 TTL: 30 分钟（P2-12 提升，字典数据变更频率极低） */
const STORAGE_TTL = 30 * 60 * 1000

interface CachedDict {
  items: DictItemVO[]
  /** 缓存时间戳（ms） */
  timestamp: number
}

export const useDictStore = defineStore('dict', () => {
  /** 内存缓存：typeCode → 字典项列表 */
  const dictMap = ref<Map<string, DictItemVO[]>>(new Map())
  /** 加载中的 typeCode 集合（防重复请求） */
  const loadingSet = ref<Set<string>>(new Set())

  /**
   * 从 localStorage 读取缓存
   */
  function readStorage(typeCode: string): DictItemVO[] | null {
    try {
      const raw = localStorage.getItem(STORAGE_PREFIX + typeCode)
      if (!raw) return null
      const cached: CachedDict = JSON.parse(raw)
      if (Date.now() - cached.timestamp > STORAGE_TTL) {
        localStorage.removeItem(STORAGE_PREFIX + typeCode)
        return null
      }
      return cached.items
    } catch {
      return null
    }
  }

  /**
   * 写入 localStorage 缓存
   */
  function writeStorage(typeCode: string, items: DictItemVO[]): void {
    try {
      const data: CachedDict = { items, timestamp: Date.now() }
      localStorage.setItem(STORAGE_PREFIX + typeCode, JSON.stringify(data))
    } catch {
      // localStorage 满或不可用时静默降级
    }
  }

  /**
   * 加载字典数据（优先内存 → localStorage → 后端 API）
   * @param typeCode - 字典类型编码
   * @param force - 是否强制刷新（跳过缓存）
   */
  async function loadDict(typeCode: string, force = false): Promise<void> {
    // 内存缓存命中
    if (!force && dictMap.value.has(typeCode)) return

    // localStorage 缓存命中
    if (!force) {
      const cached = readStorage(typeCode)
      if (cached) {
        dictMap.value.set(typeCode, cached)
        return
      }
    }

    // 防重复请求
    if (loadingSet.value.has(typeCode)) return
    loadingSet.value.add(typeCode)

    try {
      const res = await listDictItems(typeCode)
      const items = res.data ?? []
      dictMap.value.set(typeCode, items)
      writeStorage(typeCode, items)
    } catch {
      // 请求失败时保留空数组，避免后续重复请求
      dictMap.value.set(typeCode, [])
    } finally {
      loadingSet.value.delete(typeCode)
    }
  }

  /**
   * 批量加载多个字典类型
   */
  async function loadDicts(typeCodes: string[], force = false): Promise<void> {
    await Promise.all(typeCodes.map((code) => loadDict(code, force)))
  }

  /**
   * 获取字典项列表（需先 loadDict）
   */
  function getDictItems(typeCode: string): DictItemVO[] {
    return dictMap.value.get(typeCode) ?? []
  }

  /**
   * 获取字典项的 label
   */
  function getDictLabel(typeCode: string, value: string | undefined | null): string {
    if (!value) return '-'
    const items = getDictItems(typeCode)
    return items.find((item) => item.itemCode === value)?.itemValue ?? '-'
  }

  /**
   * 清除指定字典缓存（或全部清除）
   */
  function clearCache(typeCode?: string): void {
    if (typeCode) {
      dictMap.value.delete(typeCode)
      localStorage.removeItem(STORAGE_PREFIX + typeCode)
    } else {
      dictMap.value.clear()
      // 清除所有 pmis_dict_ 前缀的 localStorage
      Object.keys(localStorage)
        .filter((key) => key.startsWith(STORAGE_PREFIX))
        .forEach((key) => localStorage.removeItem(key))
    }
  }

  return {
    dictMap,
    loadingSet,
    loadDict,
    loadDicts,
    getDictItems,
    getDictLabel,
    clearCache,
  }
})
