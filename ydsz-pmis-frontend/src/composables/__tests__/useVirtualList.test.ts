/**
 * @file useVirtualList 单元测试
 * @description 测试虚拟列表 composable 的计算逻辑正确性
 */
import { describe, it, expect } from 'vitest'
import { ref, nextTick } from 'vue'
import { useVirtualList } from '@/composables/useVirtualList'

describe('useVirtualList', () => {
  function generateData(count: number): Array<{ id: number; name: string }> {
    return Array.from({ length: count }, (_, i) => ({ id: i, name: `Item ${i}` }))
  }

  it('应该正确计算总高度', () => {
    const data = ref(generateData(1000))
    const { innerHeight } = useVirtualList(data, { itemHeight: 48, viewportHeight: 400 })

    expect(innerHeight.value).toBe(48000)
  })

  it('应该正确计算初始可视区域', () => {
    const data = ref(generateData(1000))
    const { startIndex, endIndex, visibleData } = useVirtualList(data, {
      itemHeight: 48,
      viewportHeight: 400,
      overscan: 5,
    })

    // 初始 scrollTop = 0，startIndex = max(0, 0 - 5) = 0
    expect(startIndex.value).toBe(0)
    // visibleCount = ceil(400/48) = 9, endIndex = 0 + 9 + 10 = 19
    expect(endIndex.value).toBe(19)
    // 应渲染 19 项
    expect(visibleData.value.length).toBe(19)
  })

  it('visibleData 项应包含正确的索引和偏移量', () => {
    const data = ref(generateData(100))
    const { visibleData } = useVirtualList(data, {
      itemHeight: 50,
      viewportHeight: 200,
      overscan: 2,
    })

    const first = visibleData.value[0]
    expect(first.index).toBe(0)
    expect(first._offset).toBe(0)
    expect(first.data.id).toBe(0)

    const last = visibleData.value[visibleData.value.length - 1]
    // visibleCount = ceil(200/50) = 4, endIndex = 0 + 4 + 4 = 8
    expect(last.index).toBe(7)
    expect(last._offset).toBe(350)
  })

  it('应该正确计算 paddingTop', () => {
    const data = ref(generateData(1000))
    const { paddingTop } = useVirtualList(data, {
      itemHeight: 48,
      viewportHeight: 400,
      overscan: 5,
    })

    // 初始 scrollTop = 0, startIndex = 0
    expect(paddingTop.value).toBe(0)
  })

  it('数据为空时应安全返回', () => {
    const data = ref<Array<{ id: number }>>([])
    const { visibleData, innerHeight, startIndex, endIndex } = useVirtualList(data, {
      itemHeight: 48,
      viewportHeight: 400,
    })

    expect(visibleData.value.length).toBe(0)
    expect(innerHeight.value).toBe(0)
    expect(startIndex.value).toBe(0)
    expect(endIndex.value).toBe(0)
  })

  it('数据量小于可视区域时应全部渲染', () => {
    const data = ref(generateData(5))
    const { visibleData, endIndex } = useVirtualList(data, {
      itemHeight: 48,
      viewportHeight: 400,
      overscan: 5,
    })

    // endIndex = min(5, 0 + 9 + 10) = 5
    expect(endIndex.value).toBe(5)
    expect(visibleData.value.length).toBe(5)
  })

  it('应该支持自定义 getKey', () => {
    const data = ref(generateData(10))
    const { visibleData } = useVirtualList(data, {
      itemHeight: 48,
      viewportHeight: 400,
      getKey: (item) => `custom-${item.id}`,
    })

    expect(visibleData.value[0]._key).toBe('custom-0')
  })
})
