/**
 * @file Vitest 全局测试配置
 * @description 提供 Vitest 运行时全局 setup 钩子: 在 beforeAll 中以 inline factory 形式
 *   mock element-plus (保留真实组件导出, 仅替换 ElMessage / ElMessageBox 为 vi.fn),
 *   并导出 elComponents 供测试用例通过 global.components 注册全局组件,
 *   解决 jsdom 环境下模板渲染 "Component not resolved" 报错问题.
 * @module tests/setup
 */
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

/**
 * 全局初始化: 以 inline factory mock element-plus,
 * 保留真实组件导出, 仅替换 ElMessage / ElMessageBox 为 vi.fn,
 * 避免测试运行时弹出真实 UI 干扰断言.
 */
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

/**
 * 每个用例执行后清理所有 mock 调用记录, 防止用例间状态污染.
 */
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
