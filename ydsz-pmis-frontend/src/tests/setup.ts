import { beforeAll, afterEach, vi } from 'vitest'
import {
  ElButton,
  ElTable,
  ElTableColumn,
  ElTag,
  ElSwitch,
  ElInput,
  ElInputNumber,
  ElForm,
  ElFormItem,
  ElOption,
  ElSelect,
  ElDialog,
  ElMessage,
  ElMessageBox,
} from 'element-plus'

beforeAll(() => {
  // 在 importOriginal 中保留 ElMessage/ElMessageBox 的导出, 业务代码按 named import 引用
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
      ElMessageBox: {
        confirm: vi.fn().mockResolvedValue('confirm'),
        alert: vi.fn().mockResolvedValue(undefined),
        prompt: vi.fn().mockResolvedValue({ value: '' }),
      },
    }
  })
})

afterEach(() => {
  vi.clearAllMocks()
})

/**
 * 导出 Element Plus 组件映射, 供测试通过 global.components 注册
 * 用法:
 *   import { elComponents } from '@/tests/setup'
 *   mount(Component, { global: { components: elComponents } })
 */
export const elComponents = {
  ElButton,
  ElTable,
  ElTableColumn,
  ElTag,
  ElSwitch,
  ElInput,
  ElInputNumber,
  ElForm,
  ElFormItem,
  ElOption,
  ElSelect,
  ElDialog,
  ElMessage,
  ElMessageBox,
}
