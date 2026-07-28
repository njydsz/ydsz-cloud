/**
 * normal-menu 模块
 *
 * @path comm\@core\ui-kit\menu-ui\src\components\normal-menu\normal-menu.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { MenuRecordRaw } from '@ydsz-core/typings';

interface NormalMenuProps {
  /**
   * 菜单数据
   */
  activePath?: string;
  /**
   * 是否折叠
   */
  collapse?: boolean;
  /**
   * 菜单项
   */
  menus?: MenuRecordRaw[];
  /**
   * @zh_CN 是否圆润风格
   * @default true
   */
  rounded?: boolean;
  /**
   * 主题
   */
  theme?: 'dark' | 'light';
}

export type { NormalMenuProps };
