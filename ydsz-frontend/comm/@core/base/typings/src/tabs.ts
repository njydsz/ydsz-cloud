/**
 * tabs 模块
 *
 * @path comm\@core\base\typings\src\tabs.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { RouteLocationNormalized } from 'vue-router';

/**
 * 多页签（Tab）栏中单个页签的数据模型。
 *
 * @remarks
 * 直接继承 `RouteLocationNormalized`，意味着一个页签就是一条「已归一化的路由记录」，
 * 页签的标题、图标、是否可关闭等均从 `meta` 中读取，无需额外冗余字段。
 *
 * 之所以在路由之上再加 `key`，是因为同一 `path` 在动态路由（如 `/detail/:id`）
 * 或 query 不同的情况下会对应多个互不相同的页签，仅靠 `path`/`name` 无法唯一区分。
 */
export interface TabDefinition extends RouteLocationNormalized {
  /**
   * 标签页的key
   *
   * @remarks
   * 页签列表渲染与去重、关闭、拖拽排序的唯一标识，通常由完整路径（含 query）派生。
   * 为可选是为了兼容直接由路由对象构造页签的旧调用；缺省时上层会回退用 `path` 作为标识，
   * 此时同路径不同参数的页签会被合并为一个，需按业务预期显式赋值。
   */
  key?: string;
}
