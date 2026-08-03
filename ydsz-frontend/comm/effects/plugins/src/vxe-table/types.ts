/**
 * types 模块
 *
 * @path comm\effects\plugins\src\vxe-table\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  VxeGridListeners,
  VxeGridPropTypes,
  VxeGridProps as VxeTableGridProps,
  VxeUIExport,
} from 'vxe-table';

import type { Ref } from 'vue';

import type { ClassType, DeepPartial } from '@ydsz/types';

import type { BaseFormComponentType, YDSZFormProps } from '@ydsz-core/form-ui';

import { useYDSZForm } from '@ydsz-core/form-ui';

/**
 * 表格分页信息，用于在自定义 `proxyConfig.ajax.query` 中接收分页参数。
 */
export interface VxePaginationInfo {
  /** 当前页码，从 1 开始 */
  currentPage: number;
  /** 每页条数 */
  pageSize: number;
  /** 数据总条数，由上一次查询结果回填 */
  total: number;
}

interface ToolbarConfigOptions extends VxeGridPropTypes.ToolbarConfig {
  /** 是否显示切换搜索表单的按钮 */
  search?: boolean;
}

/**
 * 项目扩展后的 vxe-grid 配置项。
 *
 * @remarks
 * 在 vxe-table 原生 `VxeGridProps` 基础上，仅扩展了工具栏配置，
 * 用于支持「搜索表单折叠按钮」这一本项目特有的能力，其余字段与官方一致。
 */
export interface VxeTableGridOptions<T = any> extends VxeTableGridProps<T> {
  /** 工具栏配置 */
  toolbarConfig?: ToolbarConfigOptions;
}

/**
 * 搜索表单与表格主体之间分隔条的样式配置。
 *
 * @remarks
 * 仅当 {@link VxeGridProps.separator} 传对象形式时生效；传布尔值等价于只控制 `show`。
 */
export interface SeparatorOptions {
  /** 是否显示分隔条 */
  show?: boolean;
  /** 分隔条背景色，需为合法 CSS 颜色值；缺省时使用主题默认色 */
  backgroundColor?: string;
}

/**
 * `YDSZVxeGrid`（搜索表单 + 表格 的组合组件）的配置项。
 *
 * @remarks
 * 该类型同时承担两个角色：组件 Props 与 {@link VxeGridApi} 的状态结构，
 * 因此通过 `api.setState` 可以在运行时修改这里的任意字段。
 *
 * 合并规则：`gridOptions` / `gridEvents` 声明为 `DeepPartial`，允许只覆盖局部；
 * 但更新时数组字段（如 `columns`、`toolbarConfig.tools`）为**整体替换**语义。
 *
 * @typeParam T - 表格行数据类型
 * @typeParam D - 搜索表单所使用的组件库适配类型
 */
export interface VxeGridProps<
  T extends Record<string, any> = any,
  D extends BaseFormComponentType = BaseFormComponentType,
> {
  /**
   * 标题
   */
  tableTitle?: string;
  /**
   * 标题帮助
   */
  tableTitleHelp?: string;
  /**
   * 组件class
   */
  class?: ClassType;
  /**
   * vxe-grid class
   */
  gridClass?: ClassType;
  /**
   * vxe-grid 配置
   */
  gridOptions?: DeepPartial<VxeTableGridOptions<T>>;
  /**
   * vxe-grid 事件
   */
  gridEvents?: DeepPartial<VxeGridListeners<T>>;
  /**
   * 表单配置
   */
  formOptions?: YDSZFormProps<D>;
  /**
   * 显示搜索表单
   * @default true
   */
  showSearchForm?: boolean;
  /**
   * 搜索表单与表格主体之间的分隔条
   */
  separator?: boolean | SeparatorOptions;
}

/**
 * vxe-table 初始化所需的宿主适配参数。
 *
 * @remarks
 * 由应用层（各 app）提供，用于把「全局默认配置」与「表单实现」注入能力层，
 * 从而让 comm 层不直接依赖某个具体的组件库适配包。
 */
export interface SetupVxeTable {
  /** 全局配置回调，在 vxe-table 完成安装后执行，可在此注册格式化器、图标、默认配置等 */
  configVxeTable: (ui: VxeUIExport) => void;
  /** 应用层选定的表单实现，供表格内置搜索表单使用 */
  useYDSZForm: typeof useYDSZForm;
}
