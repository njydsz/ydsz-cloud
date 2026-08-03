/**
 * avatar 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\avatar\avatar.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { VariantProps } from 'class-variance-authority';

import { cva } from 'class-variance-authority';

/** 头像组件的 cva 样式变体（shape 与 size），返回类名生成函数 */
export const avatarVariant = cva(
  'inline-flex items-center justify-center font-normal text-foreground select-none shrink-0 bg-secondary overflow-hidden',
  {
    variants: {
      shape: {
        circle: 'rounded-full',
        square: 'rounded-md',
      },
      size: {
        base: 'h-16 w-16 text-2xl',
        lg: 'h-32 w-32 text-5xl',
        sm: 'h-10 w-10 text-xs',
      },
    },
  },
);

/**
 * 头像组件的样式变体入参类型（`shape` 与 `size`）。
 *
 * @remarks
 * 由 cva 从 `avatarVariant` 的定义自动推导，因此**新增变体只需改 cva 配置，类型自动同步**，
 * 无需手写联合类型，也不会出现二者不一致的情况。
 *
 * 注意 `avatarVariant` 未声明 `defaultVariants`，两项均不传时只会应用基础样式，
 * 头像既没有圆角也没有尺寸，实际使用中应显式指定。
 */
export type AvatarVariants = VariantProps<typeof avatarVariant>;
