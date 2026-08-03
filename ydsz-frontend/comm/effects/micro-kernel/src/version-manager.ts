/**
 * 子应用版本管理器
 *
 * 负责：
 * - 存储和查询子应用版本信息
 * - 检测版本更新
 * - 版本比较和兼容性检查
 *
 * @path comm/effects/micro-kernel/src/version-manager.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import type { Manifest } from './loader';

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

class VersionManager {
  private versions: Map<string, VersionInfo> = new Map();
  private checkInterval: number;
  private autoCheck: boolean;
  private onVersionCheck?: (result: VersionUpdateResult) => void;
  private checkTimer: null | ReturnType<typeof setInterval> = null;

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
   * 启动自动版本检查
   */
  startAutoCheck(): void {
    if (this.checkTimer) return;

    this.checkTimer = setInterval(() => {
      // 自动检查逻辑由外部提供 manifests 触发
      console.debug('[VersionManager] Auto-check triggered');
    }, this.checkInterval);
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
