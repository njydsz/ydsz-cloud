/**
 * @file useDict.test.ts
 * @description 测试 useDict 字典 composable 与工具函数
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// ========== Mock 依赖 ==========

const mockGetDictItems = vi.fn()
const mockGetDictLabel = vi.fn()
const mockLoadDict = vi.fn()
const mockLoadDicts = vi.fn()

vi.mock('@/store/modules/dict', () => ({
  useDictStore: vi.fn(() => ({
    getDictItems: mockGetDictItems,
    getDictLabel: mockGetDictLabel,
    loadDict: mockLoadDict,
    loadDicts: mockLoadDicts,
  })),
}))

// ========== 导入被测试模块 ==========
import { useDict, useDictLabel, dictToOptions, dictToStatusMap } from '@/composables/useDict'
import type { DictItemVO } from '@/api/system/dict/types'

describe('useDict', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('immediate=true 时应自动加载字典', async () => {
    mockLoadDict.mockResolvedValue(undefined)
    mockGetDictItems.mockReturnValue([
      { itemCode: '1', itemValue: '选项1' },
    ])

    const { items, loading } = useDict('test_type', true)

    expect(mockLoadDict).toHaveBeenCalledWith('test_type')
    // 等待异步加载完成
    await vi.waitFor(() => {
      expect(loading.value).toBe(false)
    })
    expect(items.value).toEqual([{ itemCode: '1', itemValue: '选项1' }])
  })

  it('immediate=false 时不应自动加载', () => {
    const { loading } = useDict('test_type', false)

    expect(mockLoadDict).not.toHaveBeenCalled()
    expect(loading.value).toBe(false)
  })

  it('reload 应强制重新加载字典', async () => {
    mockLoadDict.mockResolvedValue(undefined)
    mockGetDictItems.mockReturnValue([
      { itemCode: '2', itemValue: '已更新' },
    ])

    const { items, reload } = useDict('test_type', false)

    await reload()

    expect(mockLoadDict).toHaveBeenCalledWith('test_type', true)
    expect(items.value).toEqual([{ itemCode: '2', itemValue: '已更新' }])
  })
})

describe('useDictLabel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('应返回字典项的 label', () => {
    mockGetDictLabel.mockReturnValue('已完成')

    const label = useDictLabel('wbs_task_status', 'COMPLETED')

    expect(mockGetDictLabel).toHaveBeenCalledWith('wbs_task_status', 'COMPLETED')
    expect(label).toBe('已完成')
  })

  it('value 为 null 时应返回空字符串', () => {
    mockGetDictLabel.mockReturnValue('')

    const label = useDictLabel('wbs_task_status', null)

    expect(label).toBe('')
  })
})

describe('dictToOptions', () => {
  it('应将 DictItemVO[] 转换为 OptionVO[]', () => {
    const items: DictItemVO[] = [
      { itemCode: 'A', itemValue: '选项A' },
      { itemCode: 'B', itemValue: '选项B' },
    ]

    const options = dictToOptions(items)

    expect(options).toEqual([
      { label: '选项A', value: 'A' },
      { label: '选项B', value: 'B' },
    ])
  })

  it('空数组应返回空数组', () => {
    const options = dictToOptions([])
    expect(options).toEqual([])
  })
})

describe('dictToStatusMap', () => {
  it('应将 DictItemVO[] 转换为 Record<string, OptionVO>', () => {
    const items: DictItemVO[] = [
      { itemCode: 'ACTIVE', itemValue: '启用' },
      { itemCode: 'DISABLED', itemValue: '禁用' },
    ]

    const map = dictToStatusMap(items)

    expect(map).toEqual({
      ACTIVE: { label: '启用', value: 'ACTIVE' },
      DISABLED: { label: '禁用', value: 'DISABLED' },
    })
  })

  it('空数组应返回空对象', () => {
    const map = dictToStatusMap([])
    expect(map).toEqual({})
  })
})