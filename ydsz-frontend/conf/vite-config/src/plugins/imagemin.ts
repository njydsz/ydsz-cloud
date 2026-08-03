/**
 * 图片压缩插件配置
 *
 * 使用 vite-plugin-imagemin 在构建时自动压缩图片资源。
 * 支持 PNG、JPEG、GIF、SVG、WebP 等格式。
 *
 * @path conf/vite-config/src/plugins/imagemin.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import type { PluginOption } from 'vite';

/**
 * 创建图片压缩插件
 *
 * 仅在构建模式下生效，自动压缩项目中的图片资源。
 * 压缩率通常在 50%-80%，显著减少资源体积。
 *
 * @returns Vite 插件配置
 */
async function viteImageminPlugin(): Promise<PluginOption> {
  // 动态导入，避免开发模式加载
  const viteImagemin = await import('vite-plugin-imagemin');

  return viteImagemin.default({
    // PNG 压缩配置
    png: {
      optipng: {
        optimizationLevel: 7, // 最高压缩级别 (0-7)
        interlaced: false,
      },
    },
    // JPEG 压缩配置
    jpg: {
      mozjpeg: {
        quality: 85, // 压缩质量 (0-100)
        progressive: true, // 渐进式 JPEG
      },
    },
    // GIF 压缩配置
    gif: {
      gifsicle: {
        optimizationLevel: 3, // 最高压缩级别 (0-3)
        colors: 256, // 最大颜色数
      },
    },
    // SVG 压缩配置
    svg: {
      svgo: {
        plugins: [
          {
            name: 'preset-default',
            params: {
              overrides: {
                removeViewBox: false, // 保留 viewBox
              },
            },
          },
        ],
      },
    },
    // WebP 压缩配置
    webp: {
      cwebp: {
        quality: 85, // 压缩质量 (0-100)
      },
    },
    // 是否显示压缩日志
    verbose: true,
    // 跳过大于 10KB 的文件（可选）
    // silent: false,
  });
}

export { viteImageminPlugin };
