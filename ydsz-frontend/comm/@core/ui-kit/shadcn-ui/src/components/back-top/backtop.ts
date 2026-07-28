/**
 * backtop 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\components\back-top\backtop.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export const backtopProps = {
  /**
   * @zh_CN bottom distance.
   */
  bottom: {
    default: 40,
    type: Number,
  },
  /**
   * @zh_CN right distance.
   */
  right: {
    default: 40,
    type: Number,
  },
  /**
   * @zh_CN the target to trigger scroll.
   */
  target: {
    default: '',
    type: String,
  },
  /**
   * @zh_CN the button will not show until the scroll height reaches this value.
   */
  visibilityHeight: {
    default: 200,
    type: Number,
  },
} as const;

export interface BacktopProps {
  bottom?: number;
  isGroup?: boolean;
  right?: number;
  target?: string;
  visibilityHeight?: number;
}
