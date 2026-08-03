/**
 * lite-kernel 共享依赖清单（importmap 外置）。
 *
 * ESM 原生微前端运行时要求 Vue / Pinia / Vue Router 等核心框架在主子应用间
 * 为**单一实例**，否则 provide/inject、全局状态、依赖注入均会割裂。
 * 通过 importmap 将以下依赖标记为 external，构建产物中不打包这些依赖，
 * 由浏览器在运行时按 importmap 映射统一加载唯一的 ESM 实例。
 *
 * 版本与 pnpm catalog 对齐，importmap 插件通过 jspm generator 解析。
 * 变更流程：修改此处 → 同步 pnpm catalog → 验证主子应用均可正确加载。
 *
 * @path conf/vite-config/src/micro-shared-deps.ts
 * @author ydsz-team
 * @since 3.0.0
 */

/** 框架核心：必须外置以保证单例，版本与 [pnpm catalog](pnpm-workspace.yaml) 对齐 */
export const CORE_DEPS = [
  { name: 'vue', range: '^3.5.17' },
  { name: 'vue-router', range: '^4.5.1' },
  { name: 'pinia', range: '^3.0.3' },
] as const;

/** UI 库：包体积大，外置后主子共享同一份 ESM 实例 */
export const UI_DEPS = [
  { name: 'element-plus', range: '^2.10.2' },
  { name: '@element-plus/icons-vue', range: '^2.3.2' },
  { name: 'vxe-table', range: '^4.14.4' },
  { name: 'vxe-pc-ui', range: '^4.7.12' },
] as const;

/** 工具库：体积小但跨应用使用频率高，外置消除重复 */
export const UTIL_DEPS = [
  { name: 'axios', range: '^1.10.0' },
  { name: 'echarts', range: '^5.6.0' },
  { name: 'dayjs', range: '^1.11.13' },
  { name: 'vue-demi', range: '^0.14.10' },
] as const;

/** 全量 importmap 外置列表（= CORE + UI + UTIL） */
export const ALL_SHARED_DEPS = [...CORE_DEPS, ...UI_DEPS, ...UTIL_DEPS];

/** 仅必需外置的最小集（保守策略：仅框架核心） */
export const MINIMAL_SHARED_DEPS = [...CORE_DEPS];

/** 按策略获取共享依赖列表 */
export function getSharedDeps(strategy: 'all' | 'minimal' = 'all') {
  return strategy === 'all' ? ALL_SHARED_DEPS : MINIMAL_SHARED_DEPS;
}
