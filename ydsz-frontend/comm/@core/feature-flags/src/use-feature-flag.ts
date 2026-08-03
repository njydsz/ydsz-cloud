/**
 * 功能开关 Vue 组合式 API
 *
 * @path comm/@core/feature-flags/src/use-feature-flag.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { computed } from 'vue';

import { featureFlagsManager } from './feature-flags';

/**
 * 响应式读取单个开关状态。
 *
 * 返回 ComputedRef<boolean>，当 setEnabled / 远程加载 / resetFlag 触发解析时自动更新。
 * 未注册的开关返回 false（与 isEnabled 一致）。
 */
export function useFeatureFlag(name: string) {
  return computed(() => featureFlagsManager.isEnabled(name));
}

/**
 * 响应式读取全部已注册开关的快照。
 *
 * 适合在管理面板 / 调试浮层中批量展示。
 */
export function useAllFeatureFlags() {
  return computed(() => featureFlagsManager.getAll());
}
