/**
 * types 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\breadcrumb\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Component } from 'vue';

import type { BreadcrumbStyleType } from '@ydsz-core/typings';

/**
 * 面包屑中的单个节点，支持通过 `items` 递归嵌套形成下拉分支。
 *
 * @remarks
 * 所有字段均为可选，是为了兼容「纯展示节点」（只有标题、不可点击）与
 * 「跳转节点」（带 path）两种形态。
 */
export interface IBreadcrumb {
  /** 节点图标，可传图标组件或图标名字符串；仅在 `showIcon` 开启时渲染 */
  icon?: Component | string;
  /** 是否为首页节点。首页通常只显示图标或有特殊样式，用于与普通层级区分 */
  isHome?: boolean;
  /**
   * 子节点列表，存在时该节点渲染为可展开的下拉菜单。
   *
   * @remarks
   * 类型上支持无限层级递归，但交互上通常只展开一层；层级过深会导致下拉难以操作。
   */
  items?: IBreadcrumb[];
  /**
   * 点击后跳转的路由路径。
   *
   * @remarks
   * 省略时该节点不可点击——这正是当前页节点的预期表现（最后一级不应再跳回自身）。
   */
  path?: string;
  /** 节点显示文本；缺省时该节点渲染为空白，通常意味着上游路由 meta 缺少标题配置 */
  title?: string;
}

/**
 * 面包屑组件的 props。
 *
 * @remarks
 * 组件本身是**纯展示**的：不感知路由，节点数据完全由外部计算后传入，
 * 因此可用于非路由场景（如分步流程指示）。
 */
export interface BreadcrumbProps {
  /**
   * 面包屑节点列表，顺序即展示顺序，最后一项通常视为当前页。
   *
   * @remarks
   * 唯一必填项。传空数组时组件渲染为空，不会有占位或报错。
   */
  breadcrumbs: IBreadcrumb[];
  /** 是否渲染节点图标；关闭后即使节点配置了 `icon` 也不显示 */
  showIcon?: boolean;
  /** 展示风格（如普通/背景块样式），取值由全局主题类型定义 */
  styleType?: BreadcrumbStyleType;
}
