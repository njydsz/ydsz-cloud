/**
 * index 模块
 *
 * @path bash\vsh\src\check-circular\index.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { CAC } from 'cac';

import { extname } from 'node:path';

import { getStagedFiles } from '@ydsz/node-utils';

import { circularDepsDetect } from 'circular-dependency-scanner';

// 默认配置
const DEFAULT_CONFIG = {
  allowedExtensions: ['.cjs', '.js', '.jsx', '.mjs', '.ts', '.tsx', '.vue'],
  ignoreDirs: [
    'dist',
    '.turbo',
    'output',
    '.cache',
    'scripts',
    'internal',
    'comm/effects/request/src/',
    'comm/@core/ui-kit/menu-ui/src/',
    'comm/@core/ui-kit/popup-ui/src/',
  ],
  threshold: 0, // 循环依赖的阈值
} as const;

// 类型定义
type CircularDependencyResult = string[];

interface CheckCircularConfig {
  allowedExtensions?: string[];
  ignoreDirs?: string[];
  threshold?: number;
}

interface CommandOptions {
  config?: CheckCircularConfig;
  /** 是否发现循环依赖时以非零退出码失败（CI 环境建议开启） */
  failOnCircular?: boolean;
  staged: boolean;
  verbose: boolean;
}

// 缓存机制
const cache = new Map<string, CircularDependencyResult[]>();

/**
 * 格式化循环依赖的输出
 * @param circles - 循环依赖结果
 */
function formatCircles(circles: CircularDependencyResult[]): void {
  if (circles.length === 0) {
    console.log('✅ No circular dependencies found');
    return;
  }

  console.log('⚠️ Circular dependencies found:');
  circles.forEach((circle, index) => {
    console.log(`\nCircular dependency #${index + 1}:`);
    circle.forEach((file) => console.log(`  → ${file}`));
  });
}

/**
 * 检查项目中的循环依赖
 * @param options - 检查选项
 * @param options.staged - 是否只检查暂存区文件
 * @param options.verbose - 是否显示详细信息
 * @param options.config - 自定义配置
 * @returns Promise<void>
 */
async function checkCircular({
  config = {},
  failOnCircular = false,
  staged,
  verbose,
}: CommandOptions): Promise<void> {
  try {
    // 合并配置
    const finalConfig = {
      ...DEFAULT_CONFIG,
      ...config,
    };

    // 生成忽略模式
    const ignorePattern = `**/{${finalConfig.ignoreDirs.join(',')}}/**`;

    // 检查缓存
    const cacheKey = `${staged}-${process.cwd()}-${ignorePattern}`;
    if (cache.has(cacheKey)) {
      const cachedResults = cache.get(cacheKey);
      if (cachedResults) {
        verbose && formatCircles(cachedResults);
      }
      return;
    }

    // 检测循环依赖
    const results = await circularDepsDetect({
      absolute: staged,
      cwd: process.cwd(),
      ignore: [ignorePattern],
    });

    if (staged) {
      let files = await getStagedFiles();
      const allowedExtensions = new Set(finalConfig.allowedExtensions);

      // 过滤文件列表
      files = files.filter((file) => allowedExtensions.has(extname(file)));

      const circularFiles: CircularDependencyResult[] = [];

      for (const file of files) {
        for (const result of results) {
          const resultFiles = result.flat();
          if (resultFiles.includes(file)) {
            circularFiles.push(result);
          }
        }
      }

      // 更新缓存
      cache.set(cacheKey, circularFiles);
      verbose && formatCircles(circularFiles);
    } else {
      // 更新缓存
      cache.set(cacheKey, results);
      verbose && formatCircles(results);
    }

    // 如果发现循环依赖
    if (results.length > 0) {
      if (failOnCircular) {
        console.error(
          `\n❌ Found ${results.length} circular dependencies, build blocked.`,
        );
        process.exit(1);
      }
      console.log(
        '\n⚠️ Warning: Circular dependencies found, please check and fix',
      );
    }
  } catch (error) {
    console.error(
      '❌ Error checking circular dependencies:',
      error instanceof Error ? error.message : error,
    );
  }
}

/**
 * 定义检查循环依赖的命令
 * @param cac - CAC实例
 */
function defineCheckCircularCommand(cac: CAC): void {
  cac
    .command('check-circular')
    .option('--staged', 'Only check staged files')
    .option('--verbose', 'Show detailed information')
    .option('--fail', 'Exit with error if circular dependencies found')
    .option('--threshold <number>', 'Threshold for circular dependencies', {
      default: 0,
    })
    .option('--ignore-dirs <dirs>', 'Directories to ignore, comma separated')
    .usage('Analyze project circular dependencies')
    .action(async ({ fail, ignoreDirs, staged, threshold, verbose }) => {
      const config: CheckCircularConfig = {
        threshold: Number(threshold),
        ...(ignoreDirs && { ignoreDirs: ignoreDirs.split(',') }),
      };

      await checkCircular({
        config,
        failOnCircular: !!fail,
        staged,
        verbose: verbose ?? true,
      });
    });
}

export { type CheckCircularConfig, defineCheckCircularCommand };
