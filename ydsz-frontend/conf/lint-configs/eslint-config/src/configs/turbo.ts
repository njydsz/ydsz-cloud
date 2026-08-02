/**
 * turbo 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\turbo.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Linter } from 'eslint';

import { interopDefault } from '../util';

/**
 * 接入 turbo 配置插件，提供 monorepo 任务管线相关的 lint 规则。
 *
 * @returns ESLint flat 配置数组
 */
export async function turbo(): Promise<Linter.Config[]> {
  const [pluginTurbo] = await Promise.all([
    // @ts-expect-error - no types
    interopDefault(import('eslint-config-turbo')),
  ] as const);

  return [
    {
      plugins: {
        turbo: pluginTurbo,
      },
    },
  ];
}
