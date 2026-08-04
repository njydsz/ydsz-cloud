/**
 * micro-kernel 共享依赖清单（importmap 外置）。
 *
 * ESM 原生微前端运行时要求 Vue / Pinia / Vue Router 等核心框架在主子应用间
 * 为**单一实例**，否则 provide/inject、全局状态、依赖注入均会割裂。
 * 通过 importmap 将以下依赖标记为 external，构建产物中不打包这些依赖，
 * 由浏览器在运行时按 importmap 映射统一加载唯一的 ESM 实例。
 *
 * 版本与 pnpm catalog 对齐，importmap 插件通过 jspm generator 解析。
 * 变更流程：修改此处 → 同步 pnpm catalog → 验证主子应用均可正确加载。
 *
 * v4.0 P1-1 新增共享策略分级：
 * - `core`：仅框架核心（Vue / Vue Router / Pinia）—— 最小外置集，适合仅表单/列表等简单子应用
 * - `core-ui`：框架 + UI 库（Element Plus / VXE-Table）—— 标准策略，适合有完整 UI 交互的子应用
 * - `all`：框架 + UI + 工具库（Echarts / Dayjs 等）—— 当前默认，适合图表密集的子应用
 *
 * 子应用通过 package.json `ydsz.shareStrategy` 字段选择策略：
 * ```json
 * {
 *   "name": "@ydsz/project-web",
 *   "ydsz": { "shareStrategy": "core-ui" }
 * }
 * ```
 *
 * @path conf/vite-config/src/micro-shared-deps.ts
 * @author ydsz-team
 * @since 3.0.0
 */

// ==================== 依赖清单 ====================

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

// ==================== 策略定义 ====================

/** 共享策略类型 */
export type ShareStrategy = 'core' | 'core-ui' | 'all';

/** 全量 importmap 外置列表（= CORE + UI + UTIL） */
export const ALL_SHARED_DEPS = [...CORE_DEPS, ...UI_DEPS, ...UTIL_DEPS];

/** 仅必需外置的最小集（保守策略：仅框架核心） */
export const MINIMAL_SHARED_DEPS = [...CORE_DEPS];

/** Core + UI 标准外置集（推荐大多数子应用使用） */
export const CORE_UI_SHARED_DEPS = [...CORE_DEPS, ...UI_DEPS];

/** 策略 → 依赖列表映射 */
const STRATEGY_MAP: Record<ShareStrategy, ReadonlyArray<{ name: string; range: string }>> = {
  'core': CORE_DEPS,
  'core-ui': CORE_UI_SHARED_DEPS,
  'all': ALL_SHARED_DEPS,
};

/**
 * 按策略获取共享依赖列表
 *
 * @param strategy - 共享策略：'core' | 'core-ui' | 'all'（默认 'all'）
 * @returns 对应策略的依赖列表
 *
 * @example
 * getSharedDeps('core')      // 仅 Vue / Vue Router / Pinia
 * getSharedDeps('core-ui')   // 框架 + Element Plus / VXE-Table
 * getSharedDeps('all')       // 当前全量
 */
export function getSharedDeps(strategy: ShareStrategy = 'all') {
  return STRATEGY_MAP[strategy] || ALL_SHARED_DEPS;
}

/**
 * 获取所有可用策略清单（用于文档/验证）
 */
export function getAvailableStrategies(): ShareStrategy[] {
  return Object.keys(STRATEGY_MAP) as ShareStrategy[];
}

/**
 * 检查给定策略是否有效
 */
export function isValidStrategy(strategy: string): strategy is ShareStrategy {
  return strategy in STRATEGY_MAP;
}

/** package.json 中声明共享策略的字段路径 */
export const SHARE_STRATEGY_FIELD = 'ydsz.shareStrategy';
