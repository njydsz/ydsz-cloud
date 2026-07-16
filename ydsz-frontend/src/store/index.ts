/**
 * @file Pinia 状态管理入口
 * @description 创建并导出 Pinia 实例，统一 re-export 各业务 store 模块
 * @module store/index
 *
 * 使用方式：
 *   import pinia from '@/store'            // 在 main.ts 中 app.use(pinia)
 *   import { useUserStore } from '@/store' // 业务组件中直接调用
 */
import { createPinia } from 'pinia'

const pinia = createPinia()

export default pinia

// 业务 store 统一出口，方便调用方一行引入
export * from './modules/user'
export * from './modules/permission'
export * from './modules/app'
