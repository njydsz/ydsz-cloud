/**
 * types 模块
 *
 * @path comm\effects\common-ui\src\components\col-page\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PageProps } from '../page/types';

export interface ColPageProps extends PageProps {
  /**
   * 左侧宽度
   * @default 30
   */
  leftWidth?: number;
  leftMinWidth?: number;
  leftMaxWidth?: number;
  leftCollapsedWidth?: number;
  leftCollapsible?: boolean;
  /**
   * 右侧宽度
   * @default 70
   */
  rightWidth?: number;
  rightMinWidth?: number;
  rightCollapsedWidth?: number;
  rightMaxWidth?: number;
  rightCollapsible?: boolean;

  resizable?: boolean;
  splitLine?: boolean;
  splitHandle?: boolean;
}
