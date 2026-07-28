/**
 * command 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\configs\command.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import createCommand from 'eslint-plugin-command/config';

export async function command() {
  return [
    {
      // @ts-expect-error - no types
      ...createCommand(),
    },
  ];
}
