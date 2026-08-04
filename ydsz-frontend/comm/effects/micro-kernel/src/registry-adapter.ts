/**
 * 远程注册表适配器
 *
 * 支持从远程 JSON（/api/micro-apps/registry.json）拉取子应用注册表，
 * 失败回退到静态 MICRO_APPS。新增子应用 / 修改入口 URL 时无需重新构建与
 * 部署基座，只需更新远端 registry.json 即可生效。
 *
 * 注册表 JSON 结构：
 * {
 *   "version": "2026-08-04T12:00:00Z",
 *   "apps": [{ "name": "...", "entry": "...", "activeRule": "...", ... }]
 * }
 *
 * @path comm/effects/micro-kernel/src/registry-adapter.ts
 * @author ydsz-team
 * @since 3.7.0
 */

import type { MicroAppEntry } from '@ydsz/vite-config';
import { MICRO_APPS, getProdEntry } from '@ydsz/vite-config';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器 */
const logger = createLogger('MicroKernel');

/** 注册表拉取超时（ms） */
const REGISTRY_FETCH_TIMEOUT = 5_000;

/** 注册表版本缓存 key */
const REGISTRY_CACHE_KEY = 'ydsz_micro_apps_registry';

/** 注册表缓存有效期（ms），默认 10 分钟 */
const REGISTRY_CACHE_TTL = 10 * 60 * 1_000;

/** 注册表响应结构 */
interface RegistryResponse {
  /** 注册表生成时间（ISO 8601），用于缓存校验 */
  version: string;
  /** 子应用条目数组 */
  apps: MicroAppEntry[];
}

/** 缓存结构 */
interface RegistryCache {
  /** 远端返回的 version */
  remoteVersion: string;
  /** 缓存写入时间戳 */
  cachedAt: number;
  /** 解析后的应用配置数组 */
  apps: MicroAppEntry[];
}

/** 从 localStorage 读取缓存的注册表 */
function readRegistryCache(): RegistryCache | null {
  try {
    const raw = localStorage.getItem(REGISTRY_CACHE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as RegistryCache;
    // 结构校验
    if (!parsed.remoteVersion || !parsed.cachedAt || !Array.isArray(parsed.apps)) {
      return null;
    }
    // TTL 过期返回 null
    if (Date.now() - parsed.cachedAt > REGISTRY_CACHE_TTL) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

/** 写入注册表缓存到 localStorage */
function writeRegistryCache(remoteVersion: string, apps: MicroAppEntry[]): void {
  try {
    const cache: RegistryCache = {
      remoteVersion,
      cachedAt: Date.now(),
      apps,
    };
    localStorage.setItem(REGISTRY_CACHE_KEY, JSON.stringify(cache));
  } catch {
    // 存储失败静默（隐私模式 / 容量满）
  }
}

/**
 * 获取注册表 API 端点。
 *
 * 优先级：
 * 1. 环境变量 VITE_MICRO_APPS_REGISTRY
 * 2. 默认 '/api/micro-apps/registry.json'
 */
function getRegistryEndpoint(): string {
  return import.meta.env.VITE_MICRO_APPS_REGISTRY || '/api/micro-apps/registry.json';
}

/**
 * 带超时的 fetch 封装。
 */
async function fetchWithTimeout(url: string, timeout: number): Promise<Response> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(`Registry fetch timeout after ${timeout}ms`), timeout);

  try {
    const response = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    });
    return response;
  } finally {
    clearTimeout(timeoutId);
  }
}

/**
 * 校验注册表 JSON 结构合法性。
 */
function validateRegistry(data: unknown): data is RegistryResponse {
  if (!data || typeof data !== 'object') return false;
  const resp = data as Record<string, unknown>;
  if (typeof resp.version !== 'string') return false;
  if (!Array.isArray(resp.apps)) return false;
  // 每个 app 必须有 name / entry / activeRule
  return resp.apps.every((app) => {
    if (!app || typeof app !== 'object') return false;
    const entry = app as Record<string, unknown>;
    return typeof entry.name === 'string' && typeof entry.activeRule === 'string';
  });
}

/**
 * 拉取远程注册表。
 *
 * 失败时（网络错误、超时、结构校验失败）返回 null，调用方回退到静态配置。
 *
 * @returns 注册表应用数组，或 null（拉取失败时）
 */
async function fetchRemoteRegistry(): Promise<MicroAppEntry[] | null> {
  const endpoint = getRegistryEndpoint();

  try {
    const response = await fetchWithTimeout(endpoint, REGISTRY_FETCH_TIMEOUT);
    if (!response.ok) {
      logger.warn(`Remote registry returned HTTP ${response.status}, fallback to static config`);
      return null;
    }

    const data = await response.json();
    if (!validateRegistry(data)) {
      logger.warn('Remote registry JSON structure invalid, fallback to static config');
      return null;
    }

    // 写入缓存
    writeRegistryCache(data.version, data.apps);
    logger.info(`Remote registry loaded (version: ${data.version}, ${data.apps.length} apps)`);
    return data.apps;
  } catch (err) {
    logger.warn(`Failed to fetch remote registry: ${String(err)}, fallback to static config`);
    return null;
  }
}

/**
 * 获取子应用注册表（远程优先，回退到静态 + 缓存）。
 *
 * 解析优先级：
 * 1. 远端注册表：VITE_MICRO_APPS_REGISTRY 端点成功拉取的最新配置
 * 2. localStorage 缓存：loadUsageStats TTL 内有效的缓存数据
 * 3. 静态配置：MICRO_APPS 数组
 *
 * @param useCache - 是否启用缓存回退（默认 true），测试场景可关闭
 * @returns 解析后的 MicroAppEntry 数组
 */
export async function resolveRegistry(useCache = true): Promise<MicroAppEntry[]> {
  // 1. 优先尝试远端
  const remote = await fetchRemoteRegistry();
  if (remote && remote.length > 0) {
    return remote;
  }

  // 2. 尝试缓存
  if (useCache) {
    const cached = readRegistryCache();
    if (cached && cached.apps.length > 0) {
      logger.info(`Using cached registry (remote version: ${cached.remoteVersion})`);
      return cached.apps;
    }
  }

  // 3. 回退到静态配置
  return [...MICRO_APPS];
}

/**
 * 获取单个子应用的生产环境 entry。
 *
 * 远程配置中未显式声明 entry 时，根据开发/生产环境自动推导：
 * - dev: //localhost:{devPort}
 * - prod: /{prodPath}/
 */
export function resolveAppEntry(app: MicroAppEntry): string {
  // 显式 entry 优先（远程配置可携带完整 entry URL）
  if ((app as MicroAppEntry & { entry?: string }).entry) {
    return (app as MicroAppEntry & { entry?: string }).entry!;
  }

  // 本地开发模式：localhost:port
  if (import.meta.env.DEV) {
    return `//localhost:${app.devPort}`;
  }

  // 生产模式：prodPath 回退到 /{name}/
  return getProdEntry(app);
}

/**
 * 手动清空注册表缓存。
 *
 * 可在"强制刷新注册表"按钮调用，或在 CI/CD 发布新子应用后调用。
 */
export function clearRegistryCache(): void {
  try {
    localStorage.removeItem(REGISTRY_CACHE_KEY);
    logger.info('Registry cache cleared');
  } catch {
    // 静默
  }
}

/**
 * 主动刷新注册表（忽略缓存重新拉取）。
 *
 * 供"刷新"按钮或定时器调用，返回最新注册表。
 */
export async function refreshRegistry(): Promise<MicroAppEntry[]> {
  clearRegistryCache();
  return resolveRegistry(false);
}
