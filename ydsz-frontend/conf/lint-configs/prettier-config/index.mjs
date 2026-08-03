/**
 * Prettier 代码格式化配置。
 * 
 * @remarks
 * 提供统一格式化规则（缩进、引号、分号等），供全仓库各包复用。
 * 
 * @author ydsz-team
 * @since 1.0.0
 */
export default {
  endOfLine: 'auto',
  overrides: [
    {
      files: ['*.json5'],
      options: {
        quoteProps: 'preserve',
        singleQuote: false,
      },
    },
  ],
  plugins: ['prettier-plugin-tailwindcss'],
  printWidth: 80,
  proseWrap: 'never',
  semi: true,
  singleQuote: true,
  trailingComma: 'all',
};
