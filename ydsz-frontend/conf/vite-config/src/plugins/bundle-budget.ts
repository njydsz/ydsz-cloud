/**
 * Bundle Budget 插件 — 构建产物体积硬阈值校验
 *
 * 在 Vite 构建完成后（generateBundle / writeBundle 阶段）扫描产物列表，
 * 校验单个 chunk 与总产物体积是否超出预设阈值，超限时以非零退出码阻断构建。
 *
 * 设计目标：
 * - 零外部依赖，纯 Rollup 插件 API 实现
 * - 阈值可通过参数配置，默认值与 performance-alerts.config.mjs 对齐
 * - 仅校验 JS/CSS 产物，忽略 sourcemap 与图片等二进制资源
 *
 * @path conf/vite-config/src/plugins/bundle-budget.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { Plugin } from 'rollup';

/** Bundle Budget 配置选项 */
export interface BundleBudgetOptions {
  /** 单个 JS chunk 体积上限（字节，gzip 前），默认 512KB */
  maxChunkSize?: number;
  /** 单个 CSS 文件体积上限（字节），默认 100KB */
  maxCssSize?: number;
  /** 总产物体积上限（字节，仅 JS+CSS），默认 3MB */
  maxTotalSize?: number;
  /** 是否在超限时失败构建（CI 建议 true），默认 true */
  failOnExceed?: boolean;
}

interface AssetInfo {
  fileName: string;
  size: number;
  type: 'css' | 'js' | 'other';
}

const DEFAULTS: Required<BundleBudgetOptions> = {
  maxChunkSize: 512 * 1024, // 512KB
  maxCssSize: 100 * 1024, // 100KB
  maxTotalSize: 3 * 1024 * 1024, // 3MB
  failOnExceed: true,
};

/**
 * 格式化字节数为可读字符串。
 */
function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)}MB`;
}

/**
 * 创建 Bundle Budget 校验插件。
 *
 * @param options 阈值配置
 * @returns Rollup 插件实例
 */
export function bundleBudgetPlugin(options: BundleBudgetOptions = {}): Plugin {
  const config = { ...DEFAULTS, ...options };
  const assets: AssetInfo[] = [];
  let hasViolation = false;

  return {
    name: 'ydsz:bundle-budget',
    generateBundle(_opts, bundle) {
      for (const [fileName, chunk] of Object.entries(bundle)) {
        // 跳过 sourcemap
        if (fileName.endsWith('.map')) continue;

        let type: AssetInfo['type'] = 'other';
        if (fileName.endsWith('.js') || fileName.endsWith('.mjs')) {
          type = 'js';
        } else if (fileName.endsWith('.css')) {
          type = 'css';
        } else {
          continue; // 忽略图片/字体等二进制资源
        }

        // chunk.code 或 asset.source
        const size =
          'code' in chunk && typeof chunk.code === 'string'
            ? chunk.code.length
            : 'source' in chunk && typeof chunk.source === 'string'
              ? chunk.source.length
              : 0;

        assets.push({ fileName, size, type });
      }
    },
    writeBundle() {
      if (assets.length === 0) return;

      const violations: string[] = [];
      let totalJs = 0;
      let totalCss = 0;

      for (const asset of assets) {
        if (asset.type === 'js') {
          totalJs += asset.size;
          if (asset.size > config.maxChunkSize) {
            hasViolation = true;
            violations.push(
              `  ❌ JS chunk ${asset.fileName} ${formatBytes(asset.size)} > ${formatBytes(config.maxChunkSize)}`,
            );
          }
        } else if (asset.type === 'css') {
          totalCss += asset.size;
          if (asset.size > config.maxCssSize) {
            hasViolation = true;
            violations.push(
              `  ❌ CSS ${asset.fileName} ${formatBytes(asset.size)} > ${formatBytes(config.maxCssSize)}`,
            );
          }
        }
      }

      const total = totalJs + totalCss;
      if (total > config.maxTotalSize) {
        hasViolation = true;
        violations.push(
          `  ❌ Total JS+CSS ${formatBytes(total)} > ${formatBytes(config.maxTotalSize)}`,
        );
      }

      if (hasViolation) {
        console.warn('\n📦 Bundle Budget 校验失败:');
        console.warn(violations.join('\n'));
        console.warn(
          `\n  JS total: ${formatBytes(totalJs)} | CSS total: ${formatBytes(totalCss)}\n`,
        );

        if (config.failOnExceed) {
          // 通过设置错误标记让 Rollup 中止构建
          this.error('Bundle budget exceeded — see warnings above.');
        }
      } else {
        console.info(
          `\n📦 Bundle Budget 通过: JS ${formatBytes(totalJs)} | CSS ${formatBytes(totalCss)} | Total ${formatBytes(total)}\n`,
        );
      }
    },
  };
}
