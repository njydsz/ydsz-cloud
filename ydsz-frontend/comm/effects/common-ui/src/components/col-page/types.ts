/**
 * types 模块
 *
 * @path comm\effects\common-ui\src\components\col-page\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PageProps } from '../page/types';

/**
 * 左右双栏页面容器 `ColPage` 的 Props，在 {@link PageProps} 基础上扩展分栏布局配置。
 *
 * @remarks
 * 底层基于 `ResizablePanelGroup` 实现，所有宽度类字段的单位均为**父容器宽度的百分比**（0~100），
 * 而非像素；左右两栏之和建议为 100，否则剩余空间的分配交由面板组自行处理。
 * 组件通过 `defineExpose` 暴露 `expandLeft` / `collapseLeft`，可在父组件用模板 ref 手动折叠左栏。
 */
export interface ColPageProps extends PageProps {
  /**
   * 左栏初始宽度百分比
   * @default 30
   */
  leftWidth?: number;
  /** 左栏可拖拽的最小宽度百分比；拖到该值以下时若开启折叠则触发折叠 */
  leftMinWidth?: number;
  /** 左栏可拖拽的最大宽度百分比 */
  leftMaxWidth?: number;
  /** 左栏折叠后的宽度百分比，设为 0 表示完全隐藏 */
  leftCollapsedWidth?: number;
  /** 左栏是否可折叠；为 false 时 expandLeft / collapseLeft 不生效 */
  leftCollapsible?: boolean;
  /**
   * 右栏初始宽度百分比
   * @default 70
   */
  rightWidth?: number;
  /** 右栏可拖拽的最小宽度百分比 */
  rightMinWidth?: number;
  /** 右栏折叠后的宽度百分比 */
  rightCollapsedWidth?: number;
  /** 右栏可拖拽的最大宽度百分比 */
  rightMaxWidth?: number;
  /** 右栏是否可折叠 */
  rightCollapsible?: boolean;

  /**
   * 是否允许拖拽分割线调整两栏宽度；为 false 时布局固定为初始百分比
   * @default true
   */
  resizable?: boolean;
  /** 是否显示分栏之间的分割线 */
  splitLine?: boolean;
  /** 是否在分割线上显示可拖拽手柄（把手），便于用户感知可调整 */
  splitHandle?: boolean;
}
