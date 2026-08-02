/**
 * types 模块
 *
 * @path comm\@core\ui-kit\shadcn-ui\src\ui\button\types.ts
 * @author ydsz-team
 * @since 1.0.0
 */
export type ButtonVariantSize =
  | 'default'
  | 'icon'
  | 'lg'
  | 'sm'
  | 'xs'
  | null
  | undefined;

/**
 * 按钮的视觉风格枚举。
 *
 * @remarks
 * 与 `ButtonVariantSize` 一样手写而非由 cva 推导，因此**新增风格时必须同步修改按钮组件的
 * cva 定义与本类型**，否则会出现「类型允许但样式不存在」或反之的情况。
 *
 * 语义约定：`default` 为主操作，`destructive` 用于删除等危险操作，
 * `ghost` / `link` 为弱化的次级操作，`outline` / `secondary` 为普通次级操作。
 * 同一区域内应只有一个 `default` 按钮，以免主操作焦点分散。
 *
 * 允许 `null` / `undefined` 是为了兼容「不指定则由组件回落到默认风格」的写法。
 */
export type ButtonVariants =
  | 'default'
  | 'destructive'
  | 'ghost'
  | 'heavy'
  | 'icon'
  | 'link'
  | 'outline'
  | 'secondary'
  | null
  | undefined;
