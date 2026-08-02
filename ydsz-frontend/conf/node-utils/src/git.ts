/**
 * git 配置模块
 *
 * @path conf\node-utils\src\git.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import path from 'node:path';

import { execa } from 'execa';

export * from '@changesets/git';

/**
 * 获取当前 git 暂存区中变更的文件列表（已去重并转为绝对路径）。
 *
 * 通过 `git diff --staged` 以 NUL 分隔的方式读取文件名，
 * 关闭 submodule 递归以避免把子模块变更混入主仓库暂存集；
 * 命令执行失败时返回空数组而非抛出，保证调用方流程不被中断。
 *
 * @returns 暂存区文件的绝对路径数组，无暂存或失败时返回空数组
 */
async function getStagedFiles(): Promise<string[]> {
  try {
    const { stdout } = await execa('git', [
      '-c',
      'submodule.recurse=false',
      'diff',
      '--staged',
      '--diff-filter=ACMR',
      '--name-only',
      '--ignore-submodules',
      '-z',
    ]);

    let changedList = stdout ? stdout.replace(/\0$/, '').split('\0') : [];
    changedList = changedList.map((item) => path.resolve(process.cwd(), item));
    const changedSet = new Set(changedList);
    changedSet.delete('');
    return [...changedSet];
  } catch (error) {
    console.error('Failed to get staged files:', error);
    return [];
  }
}

export { getStagedFiles };
