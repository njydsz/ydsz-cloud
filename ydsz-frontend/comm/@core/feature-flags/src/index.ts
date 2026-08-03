/**
 * 功能开关系统入口
 *
 * 使用方式：
 *   1. bootstrap 中 initFeatureFlags({ namespace, remoteLoader })
 *   2. 应用入口注册开关定义 defineFeatureFlags([...])
 *   3. 组件中 useFeatureFlag('new-dashboard') 读取响应式状态
 *
 * @path comm/@core/feature-flags/src/index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { FeatureFlagDef, FeatureFlagsOptions } from './types';

import { featureFlagsManager } from './feature-flags';
import { useAllFeatureFlags, useFeatureFlag } from './use-feature-flag';

/**
 * 注册开关定义。
 *
 * 应在 initFeatureFlags 之前调用，使默认值在初始化时即生效；
 * 若需运行时动态新增，也可在任意时刻调用（增量合并，不覆盖已存在的远程 / 本地值）。
 */
function defineFeatureFlags(defs: FeatureFlagDef[]): void {
  featureFlagsManager.register(defs);
}

/**
 * 初始化功能开关系统。
 *
 * 必须在应用 bootstrap 中调用一次（Pinia / preferences 之后即可）。
 */
async function initFeatureFlags(options: FeatureFlagsOptions = {}): Promise<void> {
  await featureFlagsManager.init(options);
}

export {
  defineFeatureFlags,
  featureFlagsManager,
  initFeatureFlags,
  useAllFeatureFlags,
  useFeatureFlag,
};
export type {
  FeatureFlagDef,
  FeatureFlagsChangeListener,
  FeatureFlagsOptions,
  FeatureFlagValue,
} from './types';
export { FeatureFlagsManager } from './feature-flags';
