/**
 * index 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import {
  command,
  comments,
  disableds,
  ignores,
  importPluginConfig,
  javascript,
  jsdoc,
  jsonc,
  node,
  perfectionist,
  prettier,
  regexp,
  sandbox,
  test,
  turbo,
  typescript,
  unicorn,
  vue,
} from './configs';
import { customConfig } from './custom-config';

type FlatConfig = Linter.Config;

type FlatConfigPromise =
  | FlatConfig
  | FlatConfig[]
  | Promise<FlatConfig>
  | Promise<FlatConfig[]>;

/**
 * 组装完整的 ESLint flat 配置。
 *
 * 按固定顺序合并各内置配置（vue / ts / 格式化 / 自定义规则等）与用户传入的
 * 额外配置，最终 `Promise.all` 并行解析后展平返回，保证配置顺序稳定可预期。
 *
 * @param config - 用户自定义的额外 flat 配置片段，追加在预设之后
 * @returns 合并后的 ESLint flat 配置数组
 */
async function defineConfig(config: FlatConfig[] = []) {
  const configs: FlatConfigPromise[] = [
    vue(),
    javascript(),
    ignores(),
    prettier(),
    typescript(),
    jsonc(),
    disableds(),
    importPluginConfig(),
    node(),
    perfectionist(),
    comments(),
    jsdoc(),
    unicorn(),
    test(),
    regexp(),
    command(),
    turbo(),
    ...customConfig,
    ...config,
  ];

  const resolved = await Promise.all(configs);

  return resolved.flat();
}

export { defineConfig };
