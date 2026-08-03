/**
 * 字体子集化插件配置
 *
 * 使用 vite-plugin-font 在构建时自动提取字体子集。
 * 仅保留实际使用的字符，大幅减少字体文件体积。
 *
 * @path conf/vite-config/src/plugins/font.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import type { PluginOption } from 'vite';

/**
 * 创建字体子集化插件
 *
 * 仅在构建模式下生效，自动分析项目中使用的字符，
 * 生成仅包含必要字符的字体子集文件。
 *
 * 对于中文字体（通常 5-10MB），子集化后可降至 100-500KB。
 *
 * @returns Vite 插件配置
 */
async function viteFontPlugin(): Promise<PluginOption> {
  // 动态导入，避免开发模式加载
  const viteFont = await import('vite-plugin-font');

  return viteFont.default({
    // 字体文件匹配模式
    include: ['**/*.ttf', '**/*.otf', '**/*.woff', '**/*.woff2'],
    // 排除 node_modules 中的字体
    exclude: ['**/node_modules/**'],
    // 是否生成原始字体的备份
    backup: false,
    // 是否显示处理日志
    verbose: true,
    // 字符集配置
    subsets: {
      // 自动检测项目中使用的字符
      auto: true,
      // 额外包含的字符范围（可选）
      // 例如：数字、基本标点
      includeRanges: [
        [0x0020, 0x007e], // ASCII 可打印字符
        [0x00a0, 0x00ff], // Latin-1 补充
        [0x2000, 0x206f], // 通用标点
        [0x3000, 0x303f], // CJK 符号和标点
        [0xff00, 0xffef], // 全角 ASCII、半角片假名
      ],
    },
    // 输出格式配置
    output: {
      // 生成 woff2 格式（现代浏览器支持，压缩率最高）
      woff2: true,
      // 生成 woff 格式（兼容性更好）
      woff: true,
      // 是否保留原始格式
      original: false,
    },
    // CSS 更新配置
    css: {
      // 自动更新 CSS 中的字体引用
      update: true,
      // 是否添加 font-display: swap
      fontDisplay: 'swap',
    },
  });
}

export { viteFontPlugin };
