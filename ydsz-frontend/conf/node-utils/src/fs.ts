/**
 * fs 配置模块
 *
 * @path conf\node-utils\src\fs.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import { promises as fs } from 'node:fs';
import { dirname } from 'node:path';

/**
 * 将对象序列化为 JSON 并写入文件（自动创建父目录）。
 *
 * @param filePath - 目标文件绝对或相对路径
 * @param data - 待序列化的任意数据
 * @param spaces - JSON 缩进空格数，默认 2
 * @returns 写入完成后 resolve 的 Promise
 * @throws 写入失败时抛出原始错误
 */
export async function outputJSON(
  filePath: string,
  data: any,
  spaces: number = 2,
) {
  try {
    const dir = dirname(filePath);
    await fs.mkdir(dir, { recursive: true });
    const jsonData = JSON.stringify(data, null, spaces);
    await fs.writeFile(filePath, jsonData, 'utf8');
  } catch (error) {
    console.error('Error writing JSON file:', error);
    throw error;
  }
}

/**
 * 确保文件存在，不存在则创建空文件（自动创建父目录）。
 *
 * 采用追加模式打开，已存在时不会清空或覆盖原有内容。
 *
 * @param filePath - 目标文件绝对或相对路径
 * @throws 创建失败时抛出原始错误
 */
export async function ensureFile(filePath: string) {
  try {
    const dir = dirname(filePath);
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(filePath, '', { flag: 'a' });
  } catch (error) {
    console.error('Error ensuring file:', error);
    throw error;
  }
}

/**
 * 读取并解析 JSON 文件。
 *
 * @param filePath - 待读取的 JSON 文件绝对或相对路径
 * @returns 解析后的对象
 * @throws 读取或 JSON 解析失败时抛出原始错误
 */
export async function readJSON(filePath: string) {
  try {
    const data = await fs.readFile(filePath, 'utf8');
    return JSON.parse(data);
  } catch (error) {
    console.error('Error reading JSON file:', error);
    throw error;
  }
}
