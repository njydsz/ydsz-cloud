/**
 * command 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\command.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import createCommand from 'eslint-plugin-command/config';

/**
 * 提供 eslint-plugin-command 的交互式代码修正配置。
 *
 * 该插件支持在编辑器内通过注释触发批量代码重构，此处直接展开其默认配置。
 *
 * @returns 单条 ESLint flat 配置数组
 */
export async function command() {
  return [
    {
      // @ts-expect-error - no types
      ...createCommand(),
    },
  ];
}
