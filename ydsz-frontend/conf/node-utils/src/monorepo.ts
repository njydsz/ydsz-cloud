/**
 * monorepo 配置模块
 *
 * @path conf\node-utils\src\monorepo.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { dirname } from 'node:path';

import {
  getPackages as getPackagesFunc,
  getPackagesSync as getPackagesSyncFunc,
} from '@manypkg/get-packages';
import { findUpSync } from 'find-up';

/**
 * 向上查找 pnpm 大仓（monorepo）的根目录。
 *
 * 依据 pnpm-lock.yaml 文件定位仓库根（pnpm 工作区根必定包含该锁文件），
 * 默认从当前工作目录开始向上搜索。
 *
 * @param cwd - 起始查找目录，默认 process.cwd()
 * @returns 大仓根目录的绝对路径
 */
function findMonorepoRoot(cwd: string = process.cwd()) {
  const lockFile = findUpSync('pnpm-lock.yaml', {
    cwd,
    type: 'file',
  });
  return dirname(lockFile || '');
}

/**
 * 同步获取大仓中所有包的元信息。
 *
 * 先定位大仓根再委托 @manypkg/get-packages 解析，适用于不允许 await 的同步场景。
 *
 * @returns 包含大仓所有包信息的同步结果对象
 */
function getPackagesSync() {
  const root = findMonorepoRoot();
  return getPackagesSyncFunc(root);
}

/**
 * 异步获取大仓中所有包的元信息。
 *
 * 与 {@link getPackagesSync} 行为一致，但使用异步 API 解析，避免阻塞事件循环。
 *
 * @returns 包含大仓所有包信息的异步结果对象
 */
async function getPackages() {
  const root = findMonorepoRoot();

  return await getPackagesFunc(root);
}

/**
 * 按包名在大仓中查找指定包。
 *
 * @param pkgName - 目标包的 package.json 中的 name 字段
 * @returns 命中的包信息；未找到时返回 undefined
 */
async function getPackage(pkgName: string) {
  const { packages } = await getPackages();
  return packages.find((pkg) => pkg.packageJson.name === pkgName);
}

export { findMonorepoRoot, getPackage, getPackages, getPackagesSync };
