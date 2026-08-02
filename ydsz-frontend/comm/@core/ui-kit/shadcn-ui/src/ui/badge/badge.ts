/**
 * badge 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\badge\badge.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VariantProps } from 'class-variance-authority';

import { cva } from 'class-variance-authority';

export const badgeVariants = cva(
  'inline-flex items-center rounded-md border border-border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
  {
    defaultVariants: {
      variant: 'default',
    },
    variants: {
      variant: {
        default:
          'border-transparent bg-accent hover:bg-accent text-primary-foreground shadow',
        destructive:
          'border-transparent bg-destructive text-destructive-foreground shadow hover:bg-destructive-hover',
        outline: 'text-foreground',
        secondary:
          'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80',
      },
    },
  },
);

/**
 * 徽标组件的样式变体入参类型。
 *
 * @remarks
 * 由 cva 从 `badgeVariants` 自动推导，新增变体时类型自动同步。
 * 未指定 `variant` 时回落到 `default`；语义上 `destructive` 用于错误/危险状态，
 * `outline` 为无底色描边样式，适合信息密度高、不宜大面积着色的场景。
 */
export type BadgeVariants = VariantProps<typeof badgeVariants>;
