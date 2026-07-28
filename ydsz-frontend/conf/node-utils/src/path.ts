/**
 * path 配置模块
 *
 * @path conf\node-utils\src\path.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { posix } from 'node:path';

/**
 * 将给定的文件路径转换为 POSIX 风格。
 * @param {string} pathname - 原始文件路径。
 */
function toPosixPath(pathname: string) {
  return pathname.split(`\\`).join(posix.sep);
}

export { toPosixPath };
