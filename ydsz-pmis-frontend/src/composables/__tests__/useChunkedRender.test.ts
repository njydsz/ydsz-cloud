/**
 * @file useChunkedRender 单元测试
 * @description 测试分片渲染 composable 的渐进式加载逻辑
 */
import { describe, it, expect, vi } from 'vitest'
import { ref, nextTick } from 'vue'
import { useChunkedRender } from '@/composables/useChunkedRender'

describe('useChunkedRender', () => {
  function generateData(count: number): number[] {
    return Array.from({ length: count }, (_, i) => i)
  }

  it('应该初始渲染指定数量的数据', () => {
    const data = ref(generateData(100))
    const { renderList, hasMore } = useChunkedRender(data, {
      initialCount: 20,
      chunkSize: 10,
    })

    expect(renderList.value.length).toBe(20)
    expect(hasMore.value).toBe(true)
  })

  it('loadMore 应该追加指定数量的数据', async () => {
    const data = ref(generateData(100))
    const { renderList, hasMore, loadMore } = useChunkedRender(data, {
      initialCount: 20,
      chunkSize: 15,
    })

    expect(renderList.value.length).toBe(20)

    loadMore()
    // 等待 requestAnimationFrame 回调
    await new Promise((resolve) => {
      vi.waitFor(() => {
        expect(renderList.value.length).toBe(35)
        resolve(undefined)
      }, { timeout: 1000 })
    })
    expect(hasMore.value).toBe(true)
  })

  it('数据量小于初始数量时应全部渲染', () => {
    const data = ref(generateData(5))
    const { renderList, hasMore } = useChunkedRender(data, {
      initialCount: 50,
      chunkSize: 30,
    })

    expect(renderList.value.length).toBe(5)
    expect(hasMore.value).toBe(false)
  })

  it('数据为空时应安全处理', () => {
    const data = ref<number[]>([])
    const { renderList, hasMore } = useChunkedRender(data, {
      initialCount: 50,
    })

    expect(renderList.value.length).toBe(0)
    expect(hasMore.value).toBe(false)
  })

  it('reset 应该重置为初始数量', async () => {
    const data = ref(generateData(200))
    const { renderList, loadMore, reset } = useChunkedRender(data, {
      initialCount: 20,
      chunkSize: 30,
    })

    loadMore()
    await new Promise((resolve) => {
      vi.waitFor(() => {
        if (renderList.value.length > 20) resolve(undefined)
      }, { timeout: 1000 })
    })

    expect(renderList.value.length).toBeGreaterThan(20)

    reset()
    expect(renderList.value.length).toBe(20)
  })

  it('应该尊重 maxRender 限制', async () => {
    const data = ref(generateData(10000))
    const { renderList, hasMore, loadMore } = useChunkedRender(data, {
      initialCount: 100,
      chunkSize: 500,
      maxRender: 1000,
    })

    // 连续加载直到达到 maxRender
    for (let i = 0; i < 20; i++) {
      loadMore()
      await new Promise((resolve) => setTimeout(resolve, 20))
    }

    expect(renderList.value.length).toBeLessThanOrEqual(1000)
    expect(hasMore.value).toBe(false)
  })

  it('源数据变化时应重置', () => {
    const data = ref(generateData(100))
    const { renderList } = useChunkedRender(data, {
      initialCount: 20,
    })

    expect(renderList.value.length).toBe(20)

    // 更换数据源
    data.value = generateData(50)
    expect(renderList.value.length).toBe(20) // 重置为 initialCount
  })
})
