/**
 * button 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\button\button.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { cva } from 'class-variance-authority';

/** 按钮组件的 cva 样式变体（size 与 variant），返回类名生成函数 */
export const buttonVariants = cva(
  'inline-flex items-center justify-center whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:cursor-not-allowed  disabled:opacity-50',
  {
    defaultVariants: {
      size: 'default',
      variant: 'default',
    },
    variants: {
      size: {
        default: 'h-9 px-4 py-2',
        icon: 'h-8 w-8 rounded-sm px-1 text-lg',
        lg: 'h-10 rounded-md px-4',
        sm: 'h-8 rounded-md px-2 text-xs',
        xs: 'h-8 w-8 rounded-sm px-1 text-xs',
      },
      variant: {
        default:
          'bg-primary text-primary-foreground shadow hover:bg-primary/90',
        destructive:
          'bg-destructive text-destructive-foreground shadow-sm hover:bg-destructive-hover',
        ghost: 'hover:bg-accent hover:text-accent-foreground',
        heavy: 'hover:bg-heavy hover:text-heavy-foreground',
        icon: 'hover:bg-accent hover:text-accent-foreground text-foreground/80',
        link: 'text-primary underline-offset-4 hover:underline',
        outline:
          'border border-input bg-background shadow-sm hover:bg-accent hover:text-accent-foreground',
        secondary:
          'bg-secondary text-secondary-foreground shadow-sm hover:bg-secondary/80',
      },
    },
  },
);
