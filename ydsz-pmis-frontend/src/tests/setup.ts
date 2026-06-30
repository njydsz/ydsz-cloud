import { beforeAll, afterEach, vi } from 'vitest'

beforeAll(() => {
  // 全局 mock
  vi.mock('element-plus', async (importOriginal) => {
    const actual = await importOriginal<typeof import('element-plus')>()
    return {
      ...actual,
      ElMessage: {
        success: vi.fn(),
        error: vi.fn(),
        warning: vi.fn(),
        info: vi.fn(),
      },
    }
  })
})

afterEach(() => {
  vi.clearAllMocks()
})
