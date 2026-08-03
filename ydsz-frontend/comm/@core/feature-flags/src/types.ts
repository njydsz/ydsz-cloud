/**
 * 功能开关类型定义
 *
 * @path comm/@core/feature-flags/src/types.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/**
 * 功能开关值。
 *
 * - `boolean`：开/关（最常见）
 * - `'on' | 'off'`：与远程配置 / JSON 字符串兼容
 *
 * 内部统一归一化为 boolean。
 */
export type FeatureFlagValue = boolean | 'off' | 'on';

/** 单个功能开关定义 */
export interface FeatureFlagDef {
  /** 开关唯一标识（如 `'new-dashboard'`） */
  name: string;
  /** 默认值（未配置任何来源时的兜底） */
  defaultValue: boolean;
  /** 描述（供管理面板 / 日志展示） */
  description?: string;
  /**
   * 是否允许本地覆盖（localStorage）。
   *
   * 默认 true；对于"仅运维可控"的开关可设为 false，
   * 阻止开发态 localStorage 覆盖（如计费、合规相关）。
   */
  allowLocalOverride?: boolean;
}

/** 功能开关初始化选项 */
export interface FeatureFlagsOptions {
  /** localStorage 命名空间前缀（与主应用 namespace 对齐） */
  namespace?: string;
  /**
   * 环境变量源（应用应注入 `import.meta.env`）。
   *
   * 库不直接读取 import.meta.env（作为依赖被预打包时会与宿主 env 脱钩）。
   * 从中读取 `DEV`/`MODE` 判定开发态、`VITE_FEATURE_<NAME>` 读取构建期开关默认值。
   */
  env?: Record<string, unknown>;
  /**
   * 远程开关加载器（生产环境从配置中心拉取）。
   *
   * 返回的 map 会与本地状态合并（远程优先级高于本地默认，但低于显式本地覆盖）。
   * 失败时不抛错，仅打印警告并保留既有状态。
   */
  remoteLoader?: () => Promise<Record<string, FeatureFlagValue>>;
  /**
   * 是否在生产环境启用 localStorage 本地覆盖。
   *
   * 默认 false（生产环境本地覆盖可能被用户误操作）；
   * 开发环境始终启用，便于联调。
   */
  enableLocalOverrideInProd?: boolean;
}

/** 变更监听器 */
export type FeatureFlagsChangeListener = (
  name: string,
  value: boolean,
) => void;
