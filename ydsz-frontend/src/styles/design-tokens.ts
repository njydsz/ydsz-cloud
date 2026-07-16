/**
 * @file Design Token 定义
 * @description P2-8: 统一管理设计系统的 Design Token，与 CSS 变量同步。
 *   Token 分层架构：
 *     1. Primitive Token: 原始值（颜色色板、字号、间距）
 *     2. Semantic Token: 语义化令牌（主色、成功色、标题字号）
 *     3. Component Token: 组件级令牌（按钮圆角、输入框边框色）
 *
 *   使用方式：
 *     - SCSS: 通过 variables.scss 中的 $primary-color 等变量引用
 *     - CSS: 通过 var(--color-primary) 等 CSS 变量引用
 *     - JS: 通过本文件导出的 token 对象引用
 *
 * @module styles/design-tokens
 */

/** Primitive Color Palette */
export const colorPalette = {
  blue: {
    50: '#eff6ff',
    100: '#dbecff',
    200: '#bedaff',
    300: '#94bfff',
    400: '#6aa1ff',
    500: '#4080ff',
    600: '#1677ff',
    700: '#0958d9',
    800: '#003eb3',
    900: '#002c8f',
  },
  gray: {
    50: '#f7f8fa',
    100: '#f2f3f5',
    200: '#e5e6eb',
    300: '#c9cdd4',
    400: '#a9aeb8',
    500: '#86909c',
    600: '#6b7280',
    700: '#4e5969',
    800: '#272e3b',
    900: '#1d2129',
  },
  green: {
    50: '#e8ffea',
    100: '#c2f5c8',
    200: '#97e3a0',
    300: '#6ccb75',
    400: '#46b150',
    500: '#299432',
    600: '#1f7d27',
    700: '#15661f',
    800: '#0d4f16',
    900: '#05380d',
  },
  orange: {
    50: '#fff7e8',
    100: '#ffe8c2',
    200: '#ffd991',
    300: '#ffc564',
    400: '#faad14',
    500: '#e89200',
    600: '#c97600',
    700: '#a85b00',
    800: '#864300',
    900: '#632c00',
  },
  red: {
    50: '#fff0f0',
    100: '#ffd6d6',
    200: '#ffadad',
    300: '#ff7875',
    400: '#ff4d4f',
    500: '#f5222d',
    600: '#cf1322',
    700: '#a8071a',
    800: '#820014',
    900: '#5c0009',
  },
  purple: {
    50: '#f5f0ff',
    100: '#e0d0ff',
    200: '#c4a8ff',
    300: '#a880ff',
    400: '#8c58ff',
    500: '#722ed1',
    600: '#5b1db0',
    700: '#450f8f',
    800: '#30066e',
    900: '#1c004d',
  },
} as const

/** Semantic Tokens */
export const semanticTokens = {
  color: {
    primary: colorPalette.blue[600],
    primaryHover: colorPalette.blue[500],
    primaryActive: colorPalette.blue[700],
    success: colorPalette.green[500],
    warning: colorPalette.orange[400],
    danger: colorPalette.red[400],
    info: colorPalette.gray[500],

    textPrimary: colorPalette.gray[800],
    textRegular: colorPalette.gray[600],
    textSecondary: colorPalette.gray[500],
    textPlaceholder: colorPalette.gray[300],

    borderBase: colorPalette.gray[200],
    borderLight: colorPalette.gray[100],
    bgBase: colorPalette.gray[50],
    bgPage: '#f0f2f5',
    bgWhite: '#ffffff',
  },
  fontSize: {
    xs: '12px',
    sm: '13px',
    base: '14px',
    lg: '16px',
    xl: '18px',
    xxl: '20px',
    xxxl: '24px',
    display: '32px',
  },
  fontWeight: {
    normal: '400',
    medium: '500',
    semibold: '600',
    bold: '700',
  },
  spacing: {
    xs: '4px',
    sm: '8px',
    base: '12px',
    md: '16px',
    lg: '24px',
    xl: '32px',
    xxl: '48px',
  },
  borderRadius: {
    none: '0',
    sm: '2px',
    base: '4px',
    md: '6px',
    lg: '8px',
    xl: '12px',
    full: '9999px',
  },
  shadow: {
    none: 'none',
    sm: '0 1px 2px rgba(0, 0, 0, 0.05)',
    base: '0 2px 4px rgba(0, 0, 0, 0.12), 0 0 6px rgba(0, 0, 0, 0.04)',
    md: '0 4px 8px rgba(0, 0, 0, 0.12)',
    lg: '0 8px 16px rgba(0, 0, 0, 0.12)',
    xl: '0 16px 32px rgba(0, 0, 0, 0.12)',
  },
  zIndex: {
    base: '1',
    dropdown: '1000',
    sticky: '1020',
    fixed: '1030',
    modalBackdrop: '1040',
    modal: '1050',
    popover: '1060',
    tooltip: '1070',
    notification: '1080',
  },
  transition: {
    fast: '0.15s ease-in-out',
    base: '0.25s ease-in-out',
    slow: '0.35s ease-in-out',
  },
} as const

/** Component Tokens */
export const componentTokens = {
  button: {
    borderRadius: semanticTokens.borderRadius.base,
    paddingX: '16px',
    paddingXSm: '8px',
    paddingXLg: '24px',
    fontSize: semanticTokens.fontSize.base,
    fontSizeSm: semanticTokens.fontSize.xs,
    fontSizeLg: semanticTokens.fontSize.lg,
    height: '32px',
    heightSm: '24px',
    heightLg: '40px',
  },
  input: {
    borderRadius: semanticTokens.borderRadius.base,
    borderColor: semanticTokens.color.borderBase,
    borderColorFocus: semanticTokens.color.primary,
    height: '32px',
    heightSm: '24px',
    heightLg: '40px',
    paddingX: '12px',
  },
  card: {
    borderRadius: semanticTokens.borderRadius.lg,
    padding: semanticTokens.spacing.lg,
    shadow: semanticTokens.shadow.sm,
  },
  table: {
    headerBg: semanticTokens.color.bgBase,
    rowHoverBg: '#ecf5ff',
    borderColor: semanticTokens.color.borderLight,
    cellPaddingX: '12px',
    cellPaddingY: '8px',
  },
  tag: {
    borderRadius: semanticTokens.borderRadius.base,
    paddingX: '8px',
    paddingY: '2px',
    fontSize: semanticTokens.fontSize.xs,
  },
  dialog: {
    borderRadius: semanticTokens.borderRadius.xl,
    headerPadding: '20px 24px 12px',
    bodyPadding: '12px 24px',
    footerPadding: '12px 24px 20px',
  },
} as const

/** 完整 Token 导出 */
export const designTokens = {
  colorPalette,
  semantic: semanticTokens,
  component: componentTokens,
} as const

export type DesignTokens = typeof designTokens
