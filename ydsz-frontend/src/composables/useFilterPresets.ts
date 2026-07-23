/**
 * @file 列表筛选预设 composable
 * @description 管理列表页筛选条件的保存、加载、删除功能，持久化到 localStorage。
 *              支持多预设、按用户隔离、快捷切换。
 *
 * @module composables/useFilterPresets
 */
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { logger } from '@/utils/logger'

interface FilterPresetData {
  [presetName: string]: {
    filters: Record<string, unknown>
    createdAt: string
    isDefault?: boolean
  }
}

export function useFilterPresets(pageKey: string) {
  const userStore = useUserStore()
  const userId = userStore.userInfo?.id || 'guest'
  const storageKey = `ydsz:filter-presets:${pageKey}:${userId}`

  const presetNames = ref<string[]>([])
  const currentPresetName = ref<string | null>(null)

  function loadAll(): FilterPresetData {
    try {
      const raw = localStorage.getItem(storageKey)
      return raw ? JSON.parse(raw) as FilterPresetData : {}
    } catch {
      return {}
    }
  }

  function persistAll(data: FilterPresetData) {
    try {
      localStorage.setItem(storageKey, JSON.stringify(data))
    } catch (e) {
      logger.warn('[useFilterPresets]', '保存预设失败', e)
    }
  }

  function refreshNames() {
    const data = loadAll()
    presetNames.value = Object.keys(data)
  }

  function savePreset(name: string, filters: Record<string, unknown>) {
    const data = loadAll()
    data[name] = { filters, createdAt: new Date().toISOString() }
    persistAll(data)
    refreshNames()
    currentPresetName.value = name
  }

  function loadPreset(name: string): Record<string, unknown> | null {
    const data = loadAll()
    const preset = data[name]
    if (preset) {
      currentPresetName.value = name
      return preset.filters
    }
    return null
  }

  function deletePreset(name: string) {
    const data = loadAll()
    delete data[name]
    persistAll(data)
    refreshNames()
    if (currentPresetName.value === name) {
      currentPresetName.value = null
    }
  }

  function setDefault(name: string) {
    const data = loadAll()
    Object.keys(data).forEach((key) => { data[key].isDefault = key === name })
    persistAll(data)
  }

  function getDefaultPreset(): Record<string, unknown> | null {
    const data = loadAll()
    const entry = Object.entries(data).find(([, v]) => v.isDefault)
    return entry ? entry[1].filters : null
  }

  onMounted(refreshNames)

  return {
    presetNames,
    currentPresetName,
    savePreset,
    loadPreset,
    deletePreset,
    setDefault,
    getDefaultPreset,
  }
}

export default useFilterPresets