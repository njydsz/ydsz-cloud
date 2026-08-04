/**
 * 预加载策略增强
 *
 * 提供多种预加载策略：
 * - idle 预加载：在浏览器空闲时预加载
 * - hover 预加载：鼠标悬停时预加载
 * - visibility 预加载：根据页面可见性预加载
 * - route 预加载：根据路由预测预加载
 * - permission 预加载：基于用户权限动态调整预加载
 * - frequency 预加载：基于使用频率智能预加载
 *
 * @path comm/effects/micro-kernel/src/preload-strategy.ts
 * @author ydsz-team
 * @since 1.0.0
 */

import type { MicroAppConfig } from '@ydsz/micro-runtime';
import { createLogger } from '@ydsz-core/shared/utils';

/** 模块级日志器 */
const logger = createLogger('PreloadManager');

/** 应用使用频率统计 */
export interface AppUsageStats {
  /** 应用名称 */
  appName: string;
  /** 访问次数 */
  visitCount: number;
  /** 最后访问时间 */
  lastVisitTime: number;
  /** 平均访问间隔（ms） */
  averageInterval: number;
}

/** 权限检查函数类型 */
export type PermissionChecker = (codes: string[]) => boolean;

/** 预加载优先级 */
export type PreloadPriority = 'high' | 'medium' | 'low';

/** 预加载策略类型 */
export type PreloadStrategy = 'idle' | 'hover' | 'visibility' | 'route' | 'manual' | 'permission' | 'frequency';

/**
 * 预加载模式（prefetchStrategy）。
 *
 * 与上面按触发时机划分的 PreloadStrategy（idle/hover/visibility/...）正交，
 * prefetchStrategy 控制的是"是否预加载"以及"何时预加载"的高层语义：
 * - `eager`：立即预加载（忽略 idle 调度与网络条件），即当前默认行为
 * - `lazy`：仅 idle 时预加载，弱网（slow-2g/2g/3g 或 saveData）时不预加载
 * - `never`：不预加载
 *
 * 配合 `shouldPrefetchByStrategy()` 在 kernel.ts 的 start() 预加载分支复用
 * 既有的网络条件感知逻辑（shouldSkipPrefetchDueToNetwork）。
 */
export type PrefetchStrategy = 'eager' | 'lazy' | 'never';

/** PrefetchStrategy 配置项（用于 start options 与运行时复用） */
export interface PrefetchStrategyConfig {
  /** 预加载模式，默认 'lazy' */
  prefetchStrategy?: PrefetchStrategy;
}

/**
 * 网络条件感知 — 判断当前是否处于弱网/省流量环境。
 *
 * 依据 Network Information API（navigator.connection）：
 *   - effectiveType 为 slow-2g / 2g / 3g 视为慢速网络
 *   - saveData 为 true 表示用户开启省流量模式
 *
 * 任一命中即视为弱网。浏览器不支持 Network Information API 时返回 false。
 *
 * 注意：与 kernel.ts 中 `shouldSkipPrefetchDueToNetwork` 语义一致，
 * 此处导出便于 preload-strategy 在 lazy 模式下复用，避免逻辑重复。
 */
export function isSlowNetwork(): boolean {
  const nav = navigator as Navigator & {
    connection?: {
      effectiveType?: string;
      saveData?: boolean;
    };
  };
  const conn = nav.connection;
  if (!conn) return false;

  if (conn.saveData === true) return true;

  const effectiveType = conn.effectiveType;
  if (
    effectiveType === 'slow-2g' ||
    effectiveType === '2g' ||
    effectiveType === '3g'
  ) {
    return true;
  }

  return false;
}

/**
 * 根据 prefetchStrategy 判定是否应执行预加载。
 *
 * - `eager`：始终返回 true（立即预加载，不感知网络）
 * - `lazy`：弱网时返回 false，否则返回 true（交由调用方再走 idle 调度）
 * - `never`：始终返回 false
 *
 * 未传入时按 `lazy` 处理，与既有 P2 网络条件感知默认行为保持一致。
 */
export function shouldPrefetchByStrategy(strategy?: PrefetchStrategy): boolean {
  switch (strategy ?? 'lazy') {
    case 'eager':
      return true;
    case 'never':
      return false;
    case 'lazy':
    default:
      return !isSlowNetwork();
  }
}

/** 预加载策略配置 */
export interface PreloadStrategyOptions {
  /** 策略类型 */
  strategy: PreloadStrategy;
  /** 预加载延迟（毫秒），仅 idle 策略使用 */
  idleTimeout?: number;
  /** 预加载回调 */
  onPreload?: (appName: string) => void | Promise<void>;
  /** 权限码（permission 策略使用） */
  permissionCodes?: string[];
  /** 预加载优先级（frequency 策略使用） */
  priority?: PreloadPriority;
}

/** 预加载管理器 */
export class PreloadManager {
  private strategies: Map<string, PreloadStrategyOptions> = new Map();
  private preloadCache: Set<string> = new Set();
  private hoverListeners: Map<string, () => void> = new Map();
  private visibilityListener: (() => void) | null = null;
  private usageStats: Map<string, AppUsageStats> = new Map();
  private permissionChecker: PermissionChecker | null = null;
  private storageKey = 'ydsz_app_usage_stats';

  constructor() {
    this.loadUsageStats();
  }

  /**
   * 设置权限检查器
   *
   * @param checker - 权限检查函数，接收权限码数组返回布尔值
   */
  setPermissionChecker(checker: PermissionChecker | null): void {
    this.permissionChecker = checker;
  }

  /**
   * 记录应用访问（用于频率统计）
   *
   * @param appName - 应用名称
   */
  recordAppVisit(appName: string): void {
    const now = Date.now();
    const stats = this.usageStats.get(appName);

    if (stats) {
      const interval = now - stats.lastVisitTime;
      stats.visitCount++;
      stats.lastVisitTime = now;
      // 更新平均间隔（加权平均）
      stats.averageInterval = (stats.averageInterval * (stats.visitCount - 1) + interval) / stats.visitCount;
    } else {
      this.usageStats.set(appName, {
        appName,
        visitCount: 1,
        lastVisitTime: now,
        averageInterval: 0,
      });
    }

    this.saveUsageStats();
  }

  /**
   * 获取应用使用统计
   *
   * @param appName - 应用名称
   * @returns 应用使用统计，未记录时返回 null
   */
  getUsageStats(appName: string): AppUsageStats | null {
    return this.usageStats.get(appName) || null;
  }

  /**
   * 根据使用频率排序应用
   *
   * @returns 按访问频率降序排列的应用名称数组
   */
  getAppsByFrequency(): string[] {
    return Array.from(this.usageStats.entries())
      .sort((a, b) => b[1].visitCount - a[1].visitCount)
      .map(([appName]) => appName);
  }

  /**
   * 检查应用是否有权限预加载
   *
   * @param appName - 应用名称
   * @returns 是否有权限
   */
  hasPermission(appName: string): boolean {
    const strategy = this.strategies.get(appName);
    if (!strategy?.permissionCodes || strategy.permissionCodes.length === 0) {
      return true; // 无权限要求则默认允许
    }
    if (!this.permissionChecker) {
      return true; // 未设置权限检查器则默认允许
    }
    return this.permissionChecker(strategy.permissionCodes);
  }

  /**
   * 注册预加载策略
   */
  registerStrategy(appName: string, options: PreloadStrategyOptions): void {
    this.strategies.set(appName, options);

    // 根据策略类型设置监听器
    if (options.strategy === 'hover') {
      this.setupHoverListener(appName, options);
    } else if (options.strategy === 'visibility') {
      this.setupVisibilityListener();
    }
  }

  /**
   * 触发预加载
   */
  async triggerPreload(appName: string): Promise<void> {
    // 避免重复预加载
    if (this.preloadCache.has(appName)) {
      return;
    }

    // 权限检查：无权限则跳过
    if (!this.hasPermission(appName)) {
      logger.debug(`Skipped preload ${appName} due to permission check`);
      return;
    }

    const strategy = this.strategies.get(appName);
    if (!strategy?.onPreload) {
      return;
    }

    try {
      this.preloadCache.add(appName);
      await strategy.onPreload(appName);
      logger.debug(`Preloaded ${appName} via ${strategy.strategy} strategy`);
    } catch (error) {
      logger.warn(`Failed to preload ${appName}:`, error);
      this.preloadCache.delete(appName);
    }
  }

  /**
   * 设置 hover 预加载监听器
   */
  private setupHoverListener(appName: string, options: PreloadStrategyOptions): void {
    const listener = () => {
      void this.triggerPreload(appName);
    };

    // 查找所有可能触发该应用的元素
    const setupElementListeners = () => {
      const elements = document.querySelectorAll(`[data-preload-app="${appName}"]`);
      elements.forEach((el) => {
        el.addEventListener('mouseenter', listener, { once: true });
      });
    };

    // 初始设置
    setupElementListeners();

    // 使用 MutationObserver 监听 DOM 变化
    const observer = new MutationObserver(() => {
      setupElementListeners();
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
    });

    this.hoverListeners.set(appName, () => observer.disconnect());
  }

  /**
   * 设置可见性预加载监听器
   */
  private setupVisibilityListener(): void {
    if (this.visibilityListener) {
      return;
    }

    this.visibilityListener = () => {
      if (document.visibilityState === 'visible') {
        // 页面可见时，预加载所有 visibility 策略的应用
        for (const [appName, strategy] of this.strategies) {
          if (strategy.strategy === 'visibility') {
            void this.triggerPreload(appName);
          }
        }
      }
    };

    document.addEventListener('visibilitychange', this.visibilityListener);
  }

  /**
   * 清除预加载缓存
   */
  clearCache(appName?: string): void {
    if (appName) {
      this.preloadCache.delete(appName);
    } else {
      this.preloadCache.clear();
    }
  }

  /**
   * 保存使用统计到本地存储
   */
  private saveUsageStats(): void {
    try {
      const data = Object.fromEntries(this.usageStats);
      localStorage.setItem(this.storageKey, JSON.stringify(data));
    } catch {
      // 存储失败静默处理
    }
  }

  /**
   * 从本地存储加载使用统计
   */
  private loadUsageStats(): void {
    try {
      const data = localStorage.getItem(this.storageKey);
      if (data) {
        const parsed = JSON.parse(data);
        this.usageStats = new Map(Object.entries(parsed));
      }
    } catch {
      // 加载失败静默处理
    }
  }

  /**
   * 销毁管理器
   */
  destroy(): void {
    // 清理 hover 监听器
    for (const cleanup of this.hoverListeners.values()) {
      cleanup();
    }
    this.hoverListeners.clear();

    // 清理可见性监听器
    if (this.visibilityListener) {
      document.removeEventListener('visibilitychange', this.visibilityListener);
      this.visibilityListener = null;
    }

    this.strategies.clear();
    this.preloadCache.clear();
    this.usageStats.clear();
    this.permissionChecker = null;
  }
}

/** 全局预加载管理器实例 */
let preloadManagerInstance: PreloadManager | null = null;

/**
 * 获取或创建预加载管理器实例
 */
export function getPreloadManager(): PreloadManager {
  if (!preloadManagerInstance) {
    preloadManagerInstance = new PreloadManager();
  }
  return preloadManagerInstance;
}

/**
 * 重置预加载管理器（用于测试）
 */
export function resetPreloadManager(): void {
  preloadManagerInstance?.destroy();
  preloadManagerInstance = null;
}

/**
 * 创建 idle 预加载策略
 */
export function createIdlePreloadStrategy(
  apps: MicroAppConfig[],
  onPreload: (appName: string) => void | Promise<void>,
  idleTimeout = 2000,
): PreloadStrategyOptions[] {
  return apps.map((app) => ({
    strategy: 'idle' as const,
    idleTimeout,
    onPreload: () => {
      // 使用 requestIdleCallback 在空闲时执行
      if ('requestIdleCallback' in window) {
        return new Promise<void>((resolve) => {
          (window as any).requestIdleCallback(
            () => {
              void Promise.resolve(onPreload(app.name)).then(resolve);
            },
            { timeout: idleTimeout },
          );
        });
      } else {
        // 降级到 setTimeout
        return new Promise<void>((resolve) => {
          setTimeout(() => {
            void Promise.resolve(onPreload(app.name)).then(resolve);
          }, idleTimeout);
        });
      }
    },
  }));
}

/**
 * 创建 hover 预加载策略
 */
export function createHoverPreloadStrategy(
  apps: MicroAppConfig[],
  onPreload: (appName: string) => void | Promise<void>,
): PreloadStrategyOptions[] {
  return apps.map((app) => ({
    strategy: 'hover' as const,
    onPreload: () => onPreload(app.name),
  }));
}

/**
 * 创建路由预测预加载策略
 */
export function createRoutePreloadStrategy(
  apps: MicroAppConfig[],
  getRoutePredictions: () => string[],
  onPreload: (appName: string) => void | Promise<void>,
): PreloadStrategyOptions {
  return {
    strategy: 'route' as const,
    onPreload: async () => {
      const predictions = getRoutePredictions();
      for (const route of predictions) {
        const app = apps.find((a) => route.startsWith(a.activeRule));
        if (app) {
          await onPreload(app.name);
        }
      }
    },
  };
}
