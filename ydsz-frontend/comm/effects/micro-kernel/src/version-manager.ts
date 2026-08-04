/**
 * 子应用版本管理器
 *
 * 负责：
 * - 存储和查询子应用版本信息
 * - 主动 fetch manifest 比较版本（P0-F1）
 * - 版本更新时派发事件 + 清理 loader 缓存（P0-F1）
 * - 版本比较和兼容性检查
 *
 * @path comm/effects/micro-kernel/src/version-manager.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import type { Manifest } from './loader';
import { clearManifestCache } from './loader';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器 */
const logger = createLogger('VersionManager');

/** 版本信息存储 */
interface VersionInfo {
  appName: string;
  version: string;
  lastChecked: number;
  manifest: Manifest;
}

/** 版本更新检测结果 */
export interface VersionUpdateResult {
  appName: string;
  currentVersion: string;
  latestVersion: string;
  hasUpdate: boolean;
  manifest: Manifest;
}

/** 版本管理器配置 */
export interface VersionManagerOptions {
  /** 版本检查间隔（毫秒），默认 5 分钟 */
  checkInterval?: number;
  /** 是否在后台自动检查更新，默认 true */
  autoCheck?: boolean;
  /** 版本检查回调 */
  onVersionCheck?: (result: VersionUpdateResult) => void;
}

const STORAGE_KEY = 'micro-kernel:versions';
const DEFAULT_CHECK_INTERVAL = 5 * 60 * 1000; // 5分钟

/** 是否为开发模式（dev 模式无 manifest.json，跳过自动检查） */
const isDev =
  typeof import.meta !== 'undefined' &&
  (import.meta as { env?: Record<string, unknown> }).env?.DEV === true;

class VersionManager {
  private versions: Map<string, VersionInfo> = new Map();
  private checkInterval: number;
  private autoCheck: boolean;
  private onVersionCheck?: (result: VersionUpdateResult) => void;
  private checkTimer: null | ReturnType<typeof setInterval> = null;
  /** P0-F1: 注册的子应用入口（appName → entry URL），供自动检查 fetch manifest */
  private appEntries: Map<string, string> = new Map();
  /** 并发保护：防止上一轮检查未完成时启动新一轮 */
  private checking = false;

  constructor(options: VersionManagerOptions = {}) {
    this.checkInterval = options.checkInterval ?? DEFAULT_CHECK_INTERVAL;
    this.autoCheck = options.autoCheck ?? true;
    this.onVersionCheck = options.onVersionCheck;

    // 从存储中恢复版本信息
    this.loadFromStorage();

    // 启动自动检查
    if (this.autoCheck) {
      this.startAutoCheck();
    }
  }

  /**
   * P0-F1: 注册子应用入口，供自动检查使用。
   *
   * kernel 在 `registerApps` 时调用，将 appName → entry 映射注入版本管理器。
   * 后续自动检查会根据这些入口 URL 主动 fetch manifest.json 比较版本。
   */
  setAppEntries(entries: Map<string, string>): void {
    this.appEntries = new Map(entries);
  }

  /**
   * 更新子应用版本信息
   */
  updateVersion(appName: string, manifest: Manifest): void {
    const info: VersionInfo = {
      appName,
      version: manifest.version,
      lastChecked: Date.now(),
      manifest,
    };
    this.versions.set(appName, info);
    this.saveToStorage();
  }

  /**
   * 获取子应用当前版本
   */
  getVersion(appName: string): string | null {
    const info = this.versions.get(appName);
    return info?.version ?? null;
  }

  /**
   * 检查版本更新
   */
  async checkUpdate(appName: string, manifest: Manifest): Promise<VersionUpdateResult> {
    const currentVersion = this.getVersion(appName);
    const latestVersion = manifest.version;
    const hasUpdate = currentVersion !== null && currentVersion !== latestVersion;

    const result: VersionUpdateResult = {
      appName,
      currentVersion: currentVersion ?? 'unknown',
      latestVersion,
      hasUpdate,
      manifest,
    };

    // 更新版本信息
    this.updateVersion(appName, manifest);

    // 触发回调
    this.onVersionCheck?.(result);

    return result;
  }

  /**
   * 批量检查多个子应用的版本更新
   */
  async checkUpdates(manifests: Map<string, Manifest>): Promise<VersionUpdateResult[]> {
    const results: VersionUpdateResult[] = [];
    for (const [appName, manifest] of manifests) {
      const result = await this.checkUpdate(appName, manifest);
      results.push(result);
    }
    return results;
  }

  /**
   * 比较两个版本号
   * 返回：-1 (v1 < v2), 0 (v1 === v2), 1 (v1 > v2)
   */
  compareVersions(v1: string, v2: string): -1 | 0 | 1 {
    const parts1 = v1.split('.').map(Number);
    const parts2 = v2.split('.').map(Number);
    const len = Math.max(parts1.length, parts2.length);

    for (let i = 0; i < len; i++) {
      const n1 = parts1[i] ?? 0;
      const n2 = parts2[i] ?? 0;
      if (n1 < n2) return -1;
      if (n1 > n2) return 1;
    }
    return 0;
  }

  /**
   * 检查版本兼容性
   */
  isCompatible(requiredVersion: string, currentVersion: string): boolean {
    // 简单实现：主版本号必须相同
    const requiredMajor = requiredVersion.split('.')[0];
    const currentMajor = currentVersion.split('.')[0];
    return requiredMajor === currentMajor;
  }

  /**
   * P0-F1: 启动自动版本检查。
   *
   * 定时主动 fetch 各子应用 manifest.json，比较版本号：
   * - 检测到更新时派发 `micro-kernel:version-update` 事件
   * - 清理 loader 的 manifest 缓存，确保下次 loadApp 拉取最新 manifest
   * - 调用 onVersionCheck 回调
   *
   * dev 模式下跳过（无 manifest.json 产物）。
   * 页面不可见时跳过（节省网络请求）。
   */
  startAutoCheck(): void {
    if (this.checkTimer) return;

    // dev 模式无 manifest.json 产物，自动检查无意义
    if (isDev) {
      logger.debug('Auto-check skipped in dev mode');
      return;
    }

    // 首次延迟 10 秒检查，避免与首屏加载抢带宽
    const initialDelay = 10_000;
    setTimeout(() => this.runCheck(), initialDelay);

    this.checkTimer = setInterval(() => {
      void this.runCheck();
    }, this.checkInterval);
  }

  /**
   * P0-F1: 手动触发一次版本检查。
   *
   * 供外部（如用户主动"检查更新"按钮）调用。
   */
  async checkNow(): Promise<VersionUpdateResult[]> {
    return this.runCheck();
  }

  /**
   * P0-F1: 执行一轮版本检查。
   *
   * 遍历所有已注册的子应用入口，fetch 最新 manifest，
   * 与已存储的版本比较，检测到更新时派发事件并清理缓存。
   */
  private async runCheck(): Promise<VersionUpdateResult[]> {
    if (this.checking) {
      logger.debug('Check already in progress, skipping');
      return [];
    }

    // 页面不可见时跳过，节省网络请求
    if (typeof document !== 'undefined' && document.hidden) {
      logger.debug('Tab hidden, skipping check');
      return [];
    }

    if (this.appEntries.size === 0) {
      logger.debug('No app entries registered, skipping check');
      return [];
    }

    this.checking = true;
    const results: VersionUpdateResult[] = [];

    try {
      for (const [appName, entry] of this.appEntries) {
        try {
          const manifest = await this.fetchLatestManifest(entry);
          const result = await this.checkUpdate(appName, manifest);
          results.push(result);

          if (result.hasUpdate) {
            logger.info(
              `App "${appName}" updated: ${result.currentVersion} -> ${result.latestVersion}`,
            );

            // 派发版本更新事件，供 UI 层提示用户刷新
            window.dispatchEvent(
              new CustomEvent('micro-kernel:version-update', { detail: result }),
            );

            // 清理 loader 的 manifest 缓存，确保下次 loadApp 拉取新版本
            clearManifestCache();
          }
        } catch {
          // 单个应用检查失败不阻塞其他应用
          logger.debug(`Failed to check version for "${appName}"`);
        }
      }
    } finally {
      this.checking = false;
    }

    return results;
  }

  /**
   * P0-F1: 直接 fetch manifest.json（绕过 loader 缓存）。
   *
   * 自动检查需要获取最新的 manifest 来比较版本，
   * 不能使用 loader 的 manifestCache（其中可能缓存了旧版本）。
   */
  private async fetchLatestManifest(entry: string): Promise<Manifest> {
    const manifestUrl = `${entry.replace(/\/$/, '')}/manifest.json`;
    const response = await fetch(manifestUrl, {
      cache: 'no-cache',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      throw new Error(`Failed to fetch manifest from ${manifestUrl}: ${response.status}`);
    }
    return (await response.json()) as Manifest;
  }

  /**
   * 停止自动版本检查
   */
  stopAutoCheck(): void {
    if (this.checkTimer) {
      clearInterval(this.checkTimer);
      this.checkTimer = null;
    }
  }

  /**
   * 清理资源
   */
  destroy(): void {
    this.stopAutoCheck();
    this.versions.clear();
    this.appEntries.clear();
  }

  /**
   * 从存储加载版本信息
   */
  private loadFromStorage(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const data = JSON.parse(stored) as Record<string, VersionInfo>;
        for (const [key, value] of Object.entries(data)) {
          this.versions.set(key, value);
        }
      }
    } catch {
      // 存储读取失败，忽略
    }
  }

  /**
   * 保存版本信息到存储
   */
  private saveToStorage(): void {
    try {
      const data: Record<string, VersionInfo> = {};
      for (const [key, value] of this.versions) {
        data[key] = value;
      }
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {
      // 存储写入失败，忽略
    }
  }
}

/** 全局版本管理器实例 */
let versionManagerInstance: VersionManager | null = null;

/**
 * 获取或创建版本管理器实例
 */
export function getVersionManager(options?: VersionManagerOptions): VersionManager {
  if (!versionManagerInstance) {
    versionManagerInstance = new VersionManager(options);
  }
  return versionManagerInstance;
}

/**
 * 重置版本管理器（用于测试）
 */
export function resetVersionManager(): void {
  versionManagerInstance?.destroy();
  versionManagerInstance = null;
}
