/**
 * Pinia 类型补丁：为 `acceptHMRUpdate` 提供宽松的类型声明。
 *
 * @remarks
 * Pinia 官方对 `acceptHMRUpdate` 的类型约束过严，在本项目
 * 「以 setup 语法定义 store + Vite HMR」的组合下会产生误报的类型错误。
 * 这里通过模块补充（module augmentation）把参数放宽为 `any`，
 * 使各 store 文件末尾的热更新样板代码可以正常通过类型检查。
 *
 * 仅为编译期声明，不产生任何运行时代码；上游修复后应移除本文件。
 *
 * @see https://github.com/vuejs/pinia/issues/2098
 * @author ydsz-team
 * @since 1.0.0
 */
// https://github.com/vuejs/pinia/issues/2098
declare module 'pinia' {
  export function acceptHMRUpdate(
    initialUseStore: any | StoreDefinition,
    hot: any,
  ): (newModule: any) => any;
}

export {};
