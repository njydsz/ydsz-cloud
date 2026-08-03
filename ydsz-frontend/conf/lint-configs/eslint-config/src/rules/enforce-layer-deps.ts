/**
 * 组件库分层约束规则（ESLint no-restricted-paths）
 *
 * 禁止跨层导入，强制依赖方向：base → composables → ui-kit → effects → main
 *
 * @path conf\lint-configs\eslint-config\src\rules\enforce-layer-deps.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

/** 分层标识 → 路径前缀模式 */
const LAYER_PATTERNS = {
  base: '@ydsz-core/{design,icons,shared,typings}',
  composables: '@ydsz-core/composables',
  featureFlags: '@ydsz-core/feature-flags',
  preferences: '@ydsz-core/preferences',
  uiKit: '@ydsz-core/{form-ui,layout-ui,menu-ui,popup-ui,shadcn-ui,tabs-ui}',
  effects: '@ydsz/{access,common-ui,hooks,layouts,locales,micro-kernel,micro-runtime,monitor,plugins,request,stores,styles,types,utils,constants,icons}',
  main: '#/',
} as const;

/**
 * 生成 no-restricted-paths zones 配置。
 *
 * 约束规则：
 *   1. base 层禁止导入 composables/ui-kit/effects/main（base 是最底层，零外部依赖）
 *   2. composables 禁止导入 ui-kit/effects/main（composables 只依赖 base）
 *   3. ui-kit 禁止导入 effects/main（UI 组件不依赖业务层）
 *   4. effects 禁止导入 main（公共业务模块不依赖主应用）
 *
 * 允许的依赖方向：base ← composables ← ui-kit ← effects ← main
 */
export function enforceLayerDepsConfig(): Linter.Config {
  return {
    files: [
      'comm/@core/base/**/*.{ts,tsx,vue}',
      'comm/@core/composables/**/*.{ts,tsx,vue}',
      'comm/@core/ui-kit/**/*.{ts,tsx,vue}',
      'comm/effects/**/*.{ts,tsx,vue}',
    ],
    rules: {
      'import/no-restricted-paths': [
        'error',
        {
          zones: [
            // 1. base 层禁止导入上层
            {
              target: { patterns: ['comm/@core/base/**/*.{ts,tsx,vue}'] },
              from: {
                patterns: [
                  '@ydsz-core/composables',
                  '@ydsz-core/{form-ui,layout-ui,menu-ui,popup-ui,shadcn-ui,tabs-ui}',
                  '@ydsz-core/feature-flags',
                  '@ydsz-core/preferences',
                  '@ydsz/*',
                  '#/*',
                ],
              },
              message:
                '[layer-deps] base 层禁止依赖上层模块（composables/ui-kit/effects/main）',
            },
            // 2. composables 禁止导入 ui-kit/effects/main
            {
              target: { patterns: ['comm/@core/composables/**/*.{ts,tsx,vue}'] },
              from: {
                patterns: [
                  '@ydsz-core/{form-ui,layout-ui,menu-ui,popup-ui,shadcn-ui,tabs-ui}',
                  '@ydsz-core/feature-flags',
                  '@ydsz-core/preferences',
                  '@ydsz/*',
                  '#/*',
                ],
              },
              message:
                '[layer-deps] composables 层禁止依赖 ui-kit/effects/main',
            },
            // 3. ui-kit 禁止导入 effects/main
            {
              target: { patterns: ['comm/@core/ui-kit/**/*.{ts,tsx,vue}'] },
              from: {
                patterns: [
                  '@ydsz/{access,common-ui,hooks,layouts,locales,micro-kernel,micro-runtime,monitor,plugins,request,stores,styles,types,utils,constants,icons}',
                  '@ydsz-core/feature-flags',
                  '@ydsz-core/preferences',
                  '#/*',
                ],
              },
              message:
                '[layer-deps] ui-kit 层禁止依赖 effects/main',
            },
            // 4. effects 禁止导入 main
            {
              target: { patterns: ['comm/effects/**/*.{ts,tsx,vue}'] },
              from: {
                patterns: ['#/*'],
              },
              message:
                '[layer-deps] effects 层禁止依赖 main（主应用业务代码）',
            },
          ],
        },
      ],
    },
  };
}

/** 导出分层模式常量，供其他规则或工具复用 */
export { LAYER_PATTERNS };
