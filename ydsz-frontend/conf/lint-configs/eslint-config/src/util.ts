/**
 * util 配置模块
 *
 * @path conf\lint-configs\eslint-config\src\util.ts
 * @author ydsz-team
 * @since 1.0.0
 */
/** 可为值本身或其 Promise 的联合类型，常用于支持同步/异步插件工厂 */
export type Awaitable<T> = Promise<T> | T;

/**
 * 兼容具名导出与默认导出的互操作，返回模块的实际默认导出。
 *
 * 部分 CJS/ESM 混用包解构后需取 `.default` 才是真实值，
 * 本函数在存在 default 时取之，否则原样返回，便于统一加载插件。
 *
 * @param m - 待解析的模块（同步值或 Promise）
 * @returns 模块默认导出（或原模块）
 */
export async function interopDefault<T>(
  m: Awaitable<T>,
): Promise<T extends { default: infer U } ? U : T> {
  const resolved = await m;
  return (resolved as any).default || resolved;
}
