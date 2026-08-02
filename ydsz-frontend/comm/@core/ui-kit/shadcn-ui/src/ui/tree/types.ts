/**
 * types 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\tree\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Arrayable } from '@vueuse/core';
import type { FlattenedItem } from 'radix-vue';

import type { Recordable } from '@ydsz-core/typings';

/**
 * 树形选择组件的 props。
 *
 * @remarks
 * 组件不假定数据结构，节点的取值、显示文本、子级、禁用态等均通过 `*Field` 系列
 * 配置项指定字段名，因此可直接消费后端返回的原始树数据而无需前端转换。
 *
 * 选中行为由 `multiple`、`checkStrictly`、`autoCheckParent` 三者共同决定，
 * 配置组合较易混淆，使用前请先阅读各字段说明。
 */
export interface TreeProps {
  /** 单选时允许取消已有选项 */
  allowClear?: boolean;
  /**
   * 非关联选择时，自动选中上级节点
   *
   * @remarks
   * 仅在 `checkStrictly` 为真（即父子不关联）时才有意义，用于「选中子节点的同时
   * 把祖先链一并选上」这类提交要求；两者都不开启时本项无效。
   */
  autoCheckParent?: boolean;
  /** 显示边框 */
  bordered?: boolean;
  /**
   * 取消父子关联选择
   *
   * @remarks
   * 默认（为假）时勾选父节点会级联选中全部子节点，父节点呈半选态；
   * 开启后父子完全独立，选中结果即用户实际点击的节点集合。
   * 后端需要精确层级（如权限点）时通常要开启，否则会收到大量非预期的子节点 ID。
   */
  checkStrictly?: boolean;
  /** 子级字段名 */
  childrenField?: string;
  /**
   * 默认展开的键
   *
   * @remarks
   * 仅在初始化时读取一次，后续修改不会重新应用；且优先级低于 `defaultExpandedLevel`。
   */
  defaultExpandedKeys?: Array<number | string>;
  /**
   * 默认展开的级别（优先级高于defaultExpandedKeys）
   *
   * @remarks
   * 按层级批量展开，适合层级深、无法预知具体 key 的场景。
   * 一旦设置，`defaultExpandedKeys` 将被完全忽略，二者不会合并生效。
   */
  defaultExpandedLevel?: number;
  /**
   * 默认值
   *
   * @remarks
   * 单选时传单值、多选时传数组。仅作为初始选中状态，不参与后续受控更新。
   */
  defaultValue?: Arrayable<number | string>;
  /** 禁用 */
  disabled?: boolean;
  /** 禁用字段名 */
  disabledField?: string;
  /**
   * 自定义节点类名
   *
   * @remarks
   * 入参是**扁平化后**的节点（含 level、index 等位置信息），而非原始树节点，
   * 可据此实现按层级着色等效果。该函数在每次渲染每个可见节点时都会调用，需保持轻量。
   */
  getNodeClass?: (item: FlattenedItem<Recordable<any>>) => string;
  /** 节点图标所在的字段名，需配合 `showIcon` 开启才会渲染 */
  iconField?: string;
  /** label字段 */
  labelField?: string;
  /** 是否多选 */
  multiple?: boolean;
  /** 显示由iconField指定的图标 */
  showIcon?: boolean;
  /**
   * 启用展开收缩动画
   *
   * @remarks
   * 节点数量大时动画会带来明显的渲染开销，大数据量场景建议关闭。
   */
  transition?: boolean;
  /**
   * 树数据
   *
   * @remarks
   * 唯一必填项，为原始嵌套结构数组；节点内各字段的含义由 `valueField`、`labelField`、
   * `childrenField` 等配置项指定。要求 `valueField` 对应的值在**整棵树内全局唯一**，
   * 重复会导致选中态错乱。
   */
  treeData: Recordable<any>[];
  /** 值字段 */
  valueField?: string;
}
