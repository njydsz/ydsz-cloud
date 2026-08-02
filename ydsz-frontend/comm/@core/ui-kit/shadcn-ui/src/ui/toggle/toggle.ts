/**
 * toggle 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\toggle\toggle.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VariantProps } from 'class-variance-authority';

import { cva } from 'class-variance-authority';

export const toggleVariants = cva(
  'inline-flex items-center justify-center rounded-md text-sm font-medium transition-colors hover:bg-muted hover:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 data-[state=on]:bg-accent data-[state=on]:text-accent-foreground',
  {
    defaultVariants: {
      size: 'default',
      variant: 'default',
    },
    variants: {
      size: {
        default: 'h-9 px-3',
        lg: 'h-10 px-3',
        sm: 'h-8 px-2',
      },
      variant: {
        default: 'bg-transparent',
        outline:
          'border border-input bg-transparent shadow-sm hover:bg-accent hover:text-accent-foreground',
      },
    },
  },
);

/**
 * 切换按钮（Toggle）的样式变体入参类型（`size` 与 `variant`）。
 *
 * @remarks
 * 由 cva 从 `toggleVariants` 自动推导，两项均有默认值 `'default'`。
 *
 * 选中态样式通过 `data-[state=on]` 属性选择器驱动，而非依赖类名切换，
 * 因此该样式**要求宿主组件正确输出 `data-state` 属性**（radix-vue 的 Toggle 已内置）；
 * 若把这些 class 用在自定义元素上，需自行维护该属性，否则选中态不会有视觉变化。
 */
export type ToggleVariants = VariantProps<typeof toggleVariants>;
