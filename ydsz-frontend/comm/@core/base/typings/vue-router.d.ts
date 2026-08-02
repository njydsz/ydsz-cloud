/**
 * vue-router 类型增强声明，把项目自定义的 `RouteMeta` 合并进路由元信息。
 *
 * @remarks
 * 通过 TypeScript 的模块声明合并（declaration merging），让全局任意位置访问
 * `route.meta.xxx` 时都能获得 `@ydsz-core/typings` 中定义的字段提示，
 * 而无需在每个页面手动断言类型。
 *
 * 使用约束：
 * 1. 本文件必须被 `tsconfig` 的 `include` 覆盖才会生效，否则增强静默失效、
 *    表现为 `meta` 上的自定义字段报「不存在」；
 * 2. 文件顶部的 `import 'vue-router'` 不可删除——它保证 vue-router 模块已被加载，
 *    是声明合并成立的前提；
 * 3. 这是纯类型文件，编译后不产生任何运行时代码。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
/* eslint-disable no-restricted-imports */
import type { RouteMeta as IRouteMeta } from '@ydsz-core/typings';

import 'vue-router';

declare module 'vue-router' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface RouteMeta extends IRouteMeta {}
}
