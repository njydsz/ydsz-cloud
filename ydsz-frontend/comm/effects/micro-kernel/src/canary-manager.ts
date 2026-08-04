/**
 * 灰度版本分流管理器（v4.0 Canary）
 *
 * 在 VersionManager 基础上扩展灰度能力：
 * - 按用户 ID / 组织 / 自定义标签分流
 * - 多版本并发（stable + canary 同时运行）
 * - 版本回滚与一键禁用
 * - 本地缓存 + Nacos 远端拉取灰度配置
 *
 * 用法：
 *   const cm = getCanaryManager();
 *   const version = cm.resolveVersion('project-web', userId);
 *   // → { version: 'v4.0.0', tag: 'canary', percentage: 10, entry: '...' }
 *
 * @path comm/effects/micro-kernel/src/canary-manager.ts
 * @since 4.0.0
 */

import { createLogger } from '@ydsz-core/shared/utils';
import { satisfiesVersion } from '@ydsz/micro-runtime/semver';

const logger = createLogger('Canary');

/** 灰度标签 */
export type CanaryTag = 'stable' | 'canary' | 'beta' | 'alpha';

/** 单版本配置 */
export interface CanaryVersion {
  /** semver 版本号（如 '4.0.0'） */
  version: string;
  /** 灰度标签 */
  tag: CanaryTag;
  /** 该版本的入口地址（CDN 或子路径） */
  entry: string;
  /** 分流权重（0-100），stable 默认 100 */
  percentage: number;
  /** 该版本要求的最低内核版本 */
  minKernelVersion?: string;
  /** 是否已禁用 */
  disabled?: boolean;
  /** 发布时间 */
  releasedAt?: string;
}

/** 单个子应用的灰度配置 */
export interface CanaryAppConfig {
  appName: string;
  /** 默认 stable 入口 */
  stable: CanaryVersion;
  /** canary 版本列表（支持多 canary 并行） */
  canaries: CanaryVersion[];
}

/** 灰度总配置（对齐 Nacos 配置结构） */
export interface CanaryGlobalConfig {
  /** 全局开关 */
  enabled: boolean;
  /** 灰度用户白名单（命中即走 canary） */
  whitelistUserIds: string[];
  /** 灰度命中后强制指定 tag */
  forceTag?: CanaryTag;
  /** 子应用灰度配置列表 */
  apps: CanaryAppConfig[];
  /** 配置拉取的远端 URL（Nacos 或静态 JSON） */
  remoteUrl?: string;
  /** 远端缓存时长（ms），默认 60s */
  cacheTtl?: number;
}

/** 用户上下文 */
export interface CanaryUserContext {
  userId: string;
  orgId?: string;
  tags?: string[];
}

/** 分流决策结果 */
export interface CanaryResolution {
  appName: string;
  /** 命中的版本 */
  resolved: CanaryVersion;
  /** canary 标签（stable 表示未命中灰度） */
  tag: CanaryTag;
  /** 是否命中白名单 */
  whitelisted: boolean;
}

const DEFAULT_CONFIG: CanaryGlobalConfig = {
  enabled: false,
  whitelistUserIds: [],
  apps: [],
  cacheTtl: 60_000,
};

/**
 * 哈希函数：将 userId 映射到 0-100 区间，保证同一用户固定命中。
 * 使用 FNV-1a 32-bit 变体，分布均匀且零依赖。
 */
function hashToPercentage(input: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return Math.abs(h % 100);
}

/**
 * 灰度管理器单例
 */
class CanaryManager {
  private config: CanaryGlobalConfig = DEFAULT_CONFIG;
  private cacheTimestamp = 0;
  private fetchPromise: Promise<void> | null = null;

  /**
   * 初始化灰度管理器（拉取远端配置或注入本地配置）。
   *
   * @param options - 初始化选项
   */
  async init(options?: { remoteUrl?: string; fallbackConfig?: Partial<CanaryGlobalConfig> }): Promise<void> {
    const localKey = 'micro-kernel:canary-config';
    // 先从本地缓存恢复
    try {
      const cached = localStorage.getItem(localKey);
      if (cached) {
        this.config = { ...DEFAULT_CONFIG, ...JSON.parse.cached) };
        logger.info('Canary config loaded from localStorage cache');
      }
    } catch { /* noop */ }

    // 注入 fallback
    if (options?.fallbackConfig) {
      this.config = { ...this.config, ...options.fallbackConfig, apps: options.fallbackConfig.apps ?? this.config.apps };
    }

    // 拉取远端
    if (options?.remoteUrl || this.config.remoteUrl) {
      await this.refreshFromRemote();
      this.startAutoRefresh();
    }

    logger.info(`Canary manager init done, enabled=${this.config.enabled}, apps=${this.config.apps.length}`);
  }

  /**
   * 刷新远端配置
   */
  async refreshFromRemote(): Promise<void> {
    const url = this.config.remoteUrl;
    if (!url) return;

    if (this.fetchPromise) return this.fetchPromise;
    this.fetchPromise = (async () => {
      try {
        const res = await fetch(url, { cache: 'no-cache' });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const data = await res.json();
        this.config = { ...DEFAULT_CONFIG, ...data };
        this.cacheTimestamp = Date.now();
        try { localStorage.setItem('micro-kernel:canary-config', JSON.stringify(this.config)); } catch { /* noop */ }
        logger.info('Canary config refreshed from remote');
      } catch (err) {
        logger.warn(`Canary refresh failed, using cached: ${err}`);
      } finally {
        this.fetchPromise = null;
      }
    })();
    return this.fetchPromise;
  }

  /**
   * 启动自动刷新（按 cacheTtl 间隔）
   */
  startAutoRefresh(): void {
    const ttl = this.config.cacheTtl ?? 60_000;
    setInterval(() => void this.refreshFromRemote(), ttl);
  }

  /**
   * 核心分流决策。
   *
   * 决策流程：
   * 1. 灰度关闭 → 直接返回 stable
   * 2. 用户在白名单 → 强制命中 canary（或 forceTag 指定版本）
   * 3. 按 userId 哈希 < canary.percentage → 命中 canary
   * 4. 否则 → stable
   *
   * @param appName - 子应用名
   * @param user - 用户上下文
   * @returns 分流决策结果
   */
  resolveVersion(appName: string, user?: CanaryUserContext): CanaryResolution {
    const appConfig = this.config.apps.find((a) => a.appName === appName);
    // 无灰度配置 → 稳定版
    if (!appConfig || !this.config.enabled) {
      return this.defaultStableResolution(appName, appConfig);
    }

    const allVersions = [appConfig.stable, ...appConfig.canaries.filter((c) => !c.disabled)];
    const whitelisted = !!user?.userId && this.config.whitelistUserIds.includes(user.userId);

    // 白名单命中 → 强制返回指定 tag
    if (whitelisted && this.config.forceTag) {
      const forced = allVersions.find((v) => v.tag === this.config.forceTag);
      if (forced) {
        return { appName, resolved: forced, tag: forced.tag, whitelisted: true };
      }
    }

    // 未登录用户或非白名单 → stable
    if (!user?.userId) {
      return { appName, resolved: appConfig.stable, tag: 'stable', whitelisted: false };
    }

    // 按递减百分比依次尝试命中（保证 canary 叠加 stable 总和 = 100%）
    let cumulative = 0;
    for (const ver of allVersions) {
      cumulative += ver.percentage;
      const userBucket = hashToPercentage(`${appName}:${user.userId}`);
      if (userBucket < cumulative) {
        return { appName, resolved: ver, tag: ver.tag, whitelisted };
      }
    }

    return { appName, resolved: appConfig.stable, tag: 'stable', whitelisted };
  }

  private defaultStableResolution(appName: string, cfg?: CanaryAppConfig): CanaryResolution {
    if (cfg) {
      return { appName, resolved: cfg.stable, tag: 'stable', whitelisted: false };
    }
    return {
      appName,
      resolved: { version: 'stable', tag: 'stable', entry: '', percentage: 100 },
      tag: 'stable',
      whitelisted: false,
    };
  }

  /**
   * 判断版本兼容性（minKernelVersion）
   */
  isKernelCompatible(resolved: CanaryVersion, kernelVersion: string): boolean {
    if (!resolved.minKernelVersion) return true;
    return satisfiesVersion(kernelVersion, `>=${resolved.minKernelVersion}`);
  }

  /**
   * 获取当前生效的配置（调试用）
   */
  getConfig(): Readonly<CanaryGlobalConfig> {
    return this.config;
  }
}

// ==================== 单例导出 ====================

let instance: CanaryManager | null = null;

/**
 * 获取灰度管理器单例。
 */
export function getCanaryManager(): CanaryManager {
  if (!instance) {
    instance = new CanaryManager();
  }
  return instance;
}

/**
 * 重置单例（测试用）。
 */
export function resetCanaryManager(): void {
  instance = null;
}
