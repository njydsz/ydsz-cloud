/**
 * types 模块
 *
 * @path comm\@core\ui-kit\tabs-ui\src\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { IContextMenuItem } from '@ydsz-core/shadcn-ui';
import type { TabDefinition, TabsStyleType } from '@ydsz-core/typings';

/**
 * 标签页组件的自定义事件声明。
 *
 * @remarks
 * 与 Vue `emits` 选项配对的类型；注意 `sortTabs` 与 `unpin` 的载荷已经是
 * “处理结果”而非原始 DOM 事件，调用方无需再自行解析 index 或节点。
 * 拖拽排序后由 `useTabsDrag` 通过 `sortTabs` 把 old/new index 抛出，
 * 父级据此重排 `tabs` 数组即可，组件内部不维护排序状态。
 */
export type TabsEmits = {
  close: [string];
  sortTabs: [number, number];
  unpin: [TabDefinition];
};

/**
 * 标签页视图组件（TabsView）的 props。
 *
 * @remarks
 * 同一组件通过 `styleType` 支持多种视觉风格，但其中 `gap` / `maxWidth` /
 * `minWidth` 等若干属性仅在 `tabs-chrome` 风格下生效，其余风格会被忽略；
 * 这是因为 chrome 风格需要手动计算 tab 条目的间距与宽度，其他风格由
 * 滚动容器自适应。`contentClass` 默认 `tabs-chrome`，同时被 `useTabsDrag`
 * 用作定位可排序 DOM 容器的选择器 className，修改默认值需同步拖拽逻辑。
 */
export interface TabsProps {
  active?: string;
  /**
   * @zh_CN content class
   * @default tabs-chrome
   */
  contentClass?: string;
  /**
   * @zh_CN 右键菜单
   */
  contextMenus?: (data: any) => IContextMenuItem[];
  /**
   * @zh_CN 是否可以拖拽
   */
  draggable?: boolean;
  /**
   * @zh_CN 间隙
   * @default 7
   * 仅限 tabs-chrome
   */
  gap?: number;
  /**
   * @zh_CN tab 最大宽度
   * 仅限 tabs-chrome
   */
  maxWidth?: number;
  /**
   * @zh_CN 点击中键时关闭Tab
   */
  middleClickToClose?: boolean;

  /**
   * @zh_CN tab最小宽度
   * 仅限 tabs-chrome
   */
  minWidth?: number;

  /**
   * @zh_CN 是否显示图标
   */
  showIcon?: boolean;
  /**
   * @zh_CN 标签页风格
   */
  styleType?: TabsStyleType;

  /**
   * @zh_CN 选项卡数据
   */
  tabs?: TabDefinition[];

  /**
   * @zh_CN 是否响应滚轮事件
   */
  wheelable?: boolean;
}

/**
 * 经内部归一化后的标签页配置。
 *
 * @remarks
 * 在 `TabDefinition` 基础上补齐运行时需要的派生字段：`affixTab` 表示固定页
 * （不允许被拖拽挪动、也不参与排序与关闭）、`closable` 由业务配置与固定态
 * 共同决定、`icon` / `title` 为展示用资源。属于组件内部流转结构，不直接来自
 * 外部 props，因此变更不会回流到 `tabs` 数据源。
 */
export interface TabConfig extends TabDefinition {
  affixTab: boolean;
  closable: boolean;
  icon: string;
  key: string;
  title: string;
}
