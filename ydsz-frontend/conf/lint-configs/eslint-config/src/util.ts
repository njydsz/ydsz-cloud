/**
 * util 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\util.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export type Awaitable<T> = Promise<T> | T;

export async function interopDefault<T>(
  m: Awaitable<T>,
): Promise<T extends { default: infer U } ? U : T> {
  const resolved = await m;
  return (resolved as any).default || resolved;
}
