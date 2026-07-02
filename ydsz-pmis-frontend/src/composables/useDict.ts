/**
 * @file useDict composable
 * @description 字典数据获取与缓存的响应式 composable
 * @module composables/useDict
 *
 * 使用方式：
 * ```ts
 * // 在 setup 中使用
 * const { items, loading, label } = useDict('wbs_task_status')
 *
 * // 批量加载
 * const { loadDicts } = useDict()
 * await loadDicts(['wbs_task_status', 'priority_level'])
 *
 * // 获取 label
 * const label = useDictLabel('wbs_task_status', 'COMPLETED') // '已完成'
 * ```
 */
import { ref, watch, type Ref } from 'vue'
import { useDictStore } from '@/store/modules/dict'
import type { DictItemVO } from '@/api/system/dict/types'
import type { OptionVO } from '@/types/api'

/**
 * 字典数据 composable
 * @param typeCode - 字典类型编码
 * @param immediate - 是否立即加载（默认 true）
 */
export function useDict(typeCode: string, immediate = true) {
  const store = useDictStore()
  const items: Ref<DictItemVO[]> = ref([])
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      await store.loadDict(typeCode)
      items.value = store.getDictItems(typeCode)
    } finally {
      loading.value = false
    }
  }

  if (immediate) {
    load()
  }

  return {
    items,
    loading,
    reload: () => store.loadDict(typeCode, true).then(() => {
      items.value = store.getDictItems(typeCode)
    }),
  }
}

/**
 * 批量加载字典
 */
export function useDicts(typeCodes: string[], immediate = true) {
  const store = useDictStore()
  const loading = ref(false)

  async function loadAll() {
    loading.value = true
    try {
      await store.loadDicts(typeCodes)
    } finally {
      loading.value = false
    }
  }

  if (immediate) {
    loadAll()
  }

  return {
    loading,
    reload: () => store.loadDicts(typeCodes, true),
    getItems: (code: string) => store.getDictItems(code),
    getLabel: (code: string, value: string | undefined | null) => store.getDictLabel(code, value),
  }
}

/**
 * 获取字典项的 label（非响应式，适合在模板外使用）
 */
export function useDictLabel(typeCode: string, value: string | undefined | null): string {
  const store = useDictStore()
  return store.getDictLabel(typeCode, value)
}

/**
 * 将后端字典项转换为前端 OptionVO[] 格式（供 el-select / vxe-table 下拉使用）
 */
export function dictToOptions(items: DictItemVO[]): OptionVO[] {
  return items.map((item) => ({
    label: item.itemValue,
    value: item.itemCode,
  }))
}

/**
 * 将后端字典项转换为 StatusTag 组件所需的 Map 格式
 */
export function dictToStatusMap(items: DictItemVO[]): Record<string, OptionVO> {
  const map: Record<string, OptionVO> = {}
  for (const item of items) {
    map[item.itemCode] = { label: item.itemValue, value: item.itemCode }
  }
  return map
}
