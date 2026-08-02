/**
 * archiver 配置模块
 *
 * @path conf\vite-config\src\plugins\archiver.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { PluginOption } from 'vite';

import type { ArchiverPluginOptions } from '../typing';

import fs from 'node:fs';
import fsp from 'node:fs/promises';
import { join } from 'node:path';

import archiver from 'archiver';

/**
 * 构建完成后将 dist 目录压缩为 zip 归档的 Vite 插件。
 *
 * 在 closeBundle 阶段（post）延迟到事件循环末尾执行，避免阻塞主构建流程；
 * 归档名与输出目录可由 options 覆盖，默认生成 `dist.zip`。
 *
 * @param options - 归档配置（名称、输出目录），缺省使用默认值
 * @returns Vite 插件对象
 */
export const viteArchiverPlugin = (
  options: ArchiverPluginOptions = {},
): PluginOption => {
  return {
    apply: 'build',
    closeBundle: {
      handler() {
        const { name = 'dist', outputDir = '.' } = options;

        setTimeout(async () => {
          const folderToZip = 'dist';

          const zipOutputDir = join(process.cwd(), outputDir);
          const zipOutputPath = join(zipOutputDir, `${name}.zip`);
          try {
            await fsp.mkdir(zipOutputDir, { recursive: true });
          } catch {
            // ignore
          }

          try {
            await zipFolder(folderToZip, zipOutputPath);
            console.log(`Folder has been zipped to: ${zipOutputPath}`);
          } catch (error) {
            console.error('Error zipping folder:', error);
          }
        }, 0);
      },
      order: 'post',
    },
    enforce: 'post',
    name: 'vite:archiver',
  };
};

/**
 * 以流式方式将文件夹压缩为 zip 文件。
 *
 * 采用流式写入降低大目录的内存占用，压缩级别固定为 9 以求最高压缩率。
 *
 * @param folderPath - 待压缩的源文件夹路径
 * @param outputPath - 生成的 zip 文件目标路径
 * @throws 压缩过程出错时 reject
 */
async function zipFolder(
  folderPath: string,
  outputPath: string,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const output = fs.createWriteStream(outputPath);
    const archive = archiver('zip', {
      zlib: { level: 9 }, // 设置压缩级别为 9 以实现最高压缩率
    });

    output.on('close', () => {
      console.log(
        `ZIP file created: ${outputPath} (${archive.pointer()} total bytes)`,
      );
      resolve();
    });

    archive.on('error', (err) => {
      reject(err);
    });

    archive.pipe(output);

    // 使用 directory 方法以流的方式压缩文件夹，减少内存消耗
    archive.directory(folderPath, false);

    // 流式处理完成
    archive.finalize();
  });
}
