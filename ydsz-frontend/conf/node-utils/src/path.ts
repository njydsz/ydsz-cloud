/**
 * path 配置模块
 *
 * @path conf\node-utils\src\path.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { posix } from 'node:path';

/**
 * 将文件路径中的 Windows 反斜杠分隔符统一转换为 POSIX 正斜杠。
 *
 * 跨平台工具链（如打包、哈希）常要求路径分隔符一致，
 * 本方法仅替换分隔符、不做路径解析，保证映射可逆且幂等。
 *
 * @param pathname - 原始文件路径
 * @returns 使用正斜杠分隔的 POSIX 风格路径
 */
function toPosixPath(pathname: string) {
  return pathname.split(`\\`).join(posix.sep);
}

export { toPosixPath };
