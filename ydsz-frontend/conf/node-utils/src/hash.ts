/**
 * hash 配置模块
 *
 * @path conf\node-utils\src\hash.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createHash } from 'node:crypto';

/**
 * 生产基于内容的 hash，可自定义长度
 * @param content
 * @param hashLSize
 */
function generatorContentHash(content: string, hashLSize?: number) {
  const hash = createHash('md5').update(content, 'utf8').digest('hex');

  if (hashLSize) {
    return hash.slice(0, hashLSize);
  }

  return hash;
}

export { generatorContentHash };
