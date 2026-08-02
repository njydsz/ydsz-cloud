/**
 * hash 配置模块
 *
 * @path conf\node-utils\src\hash.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { createHash } from 'node:crypto';

/**
 * 生成基于内容的内容哈希（MD5 十六进制）。
 *
 * 用于根据文件/字符串内容生成稳定指纹，便于做缓存键或内容比对；
 * 传入 hashLSize 时截取前缀以控制长度，不传则返回完整 32 位 MD5。
 *
 * @param content - 参与哈希计算的原始字符串（按 utf8 编码）
 * @param hashLSize - 可选，返回哈希的前缀长度；不传则取完整哈希
 * @returns 小写的十六进制哈希字符串
 */
function generatorContentHash(content: string, hashLSize?: number) {
  const hash = createHash('md5').update(content, 'utf8').digest('hex');

  if (hashLSize) {
    return hash.slice(0, hashLSize);
  }

  return hash;
}

export { generatorContentHash };
