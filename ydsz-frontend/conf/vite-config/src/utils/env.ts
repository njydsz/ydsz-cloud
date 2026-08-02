/**
 * env 工具函数模块
 *
 * @path conf\vite-config\src\utils\env.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type { ApplicationPluginOptions } from '../typing';

import { existsSync } from 'node:fs';
import { join } from 'node:path';

import { fs } from '@ydsz/node-utils';

import dotenv from 'dotenv';

const getBoolean = (value: string | undefined) => value === 'true';

const getString = (value: string | undefined, fallback: string) =>
  value ?? fallback;

const getNumber = (value: string | undefined, fallback: number) =>
  Number(value) || fallback;

/**
 * 获取当前环境下生效的配置文件名
 */
function getConfFiles() {
  const script = process.env.npm_lifecycle_script as string;
  const reg = /--mode ([\d_a-z]+)/;
  const result = reg.exec(script);
  let mode = 'production';
  if (result) {
    mode = result[1] as string;
  }
  return ['.env', '.env.local', `.env.${mode}`, `.env.${mode}.local`];
}

/**
 * 读取并合并指定环境文件，仅保留匹配前缀的变量。
 *
 * 按优先级依次加载 .env / .env.local / .env.{mode} / .env.{mode}.local，
 * 后者覆盖前者；读取失败仅告警不中断。最终用正则过滤出以 match 开头
 * 的变量（默认 VITE_GLOB_），用于向客户端暴露白名单环境变量。
 *
 * @param match - 变量名前缀过滤规则，默认 'VITE_GLOB_'
 * @param confFiles - 待加载的环境文件名列表，默认由当前 mode 推导
 * @returns 过滤后的环境变量键值对象
 */
async function loadEnv<T = Record<string, string>>(
  match = 'VITE_GLOB_',
  confFiles = getConfFiles(),
) {
  let envConfig = {};

  for (const confFile of confFiles) {
    try {
      const confFilePath = join(process.cwd(), confFile);
      if (existsSync(confFilePath)) {
        const envPath = await fs.readFile(confFilePath, {
          encoding: 'utf8',
        });
        const env = dotenv.parse(envPath);
        envConfig = { ...envConfig, ...env };
      }
    } catch (error) {
      console.error(`Error while parsing ${confFile}`, error);
    }
  }
  const reg = new RegExp(`^(${match})`);
  Object.keys(envConfig).forEach((key) => {
    if (!reg.test(key)) {
      Reflect.deleteProperty(envConfig, key);
    }
  });
  return envConfig as T;
}

/**
 * 加载并转换环境配置为 Vite 插件可消费的结构化选项。
 *
 * 在 {@link loadEnv} 基础上，将 VITE_ 前缀的布尔/数值/字符串变量
 * 转换为插件选项所需的真实类型（如 port 转 number、compress 解析为数组），
 * 并为缺省项提供兜底默认值，避免调用方再做类型转换。
 *
 * @param match - 变量名前缀，默认 'VITE_'
 * @param confFiles - 待加载的环境文件名列表，默认由当前 mode 推导
 * @returns 含 appTitle / base / port 等字段的插件选项片段
 */
async function loadAndConvertEnv(
  match = 'VITE_',
  confFiles = getConfFiles(),
): Promise<
  Partial<ApplicationPluginOptions> & {
    appTitle: string;
    base: string;
    port: number;
  }
> {
  const envConfig = await loadEnv(match, confFiles);

  const {
    VITE_APP_TITLE,
    VITE_ARCHIVER,
    VITE_BASE,
    VITE_COMPRESS,
    VITE_DEVTOOLS,
    VITE_INJECT_APP_LOADING,
    VITE_PORT,
    VITE_PWA,
    VITE_VISUALIZER,
  } = envConfig;

  const compressTypes = (VITE_COMPRESS ?? '')
    .split(',')
    .filter((item) => item === 'brotli' || item === 'gzip');

  return {
    appTitle: getString(VITE_APP_TITLE, 'YDSZ Admin'),
    archiver: getBoolean(VITE_ARCHIVER),
    base: getString(VITE_BASE, '/'),
    compress: compressTypes.length > 0,
    compressTypes,
    devtools: getBoolean(VITE_DEVTOOLS),
    injectAppLoading: getBoolean(VITE_INJECT_APP_LOADING),
    port: getNumber(VITE_PORT, 5173),
    pwa: getBoolean(VITE_PWA),
    visualizer: getBoolean(VITE_VISUALIZER),
  };
}

export { loadAndConvertEnv, loadEnv };
