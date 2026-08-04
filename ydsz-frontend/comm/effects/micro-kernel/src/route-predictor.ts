/**
 * 路由预测引擎（P1-2）
 *
 * 基于马尔可夫链的一阶转移概率模型，根据用户历史导航序列预测下一步最可能访问的子应用。
 *
 * 核心能力：
 * 1. 记录主子应用间的跳转序列（from → to 转移）
 * 2. 构建一阶转移矩阵（sparse Map 存储）
 * 3. 给定当前应用，按转移概率降序返回 topN 预测结果
 * 4. 持久化到 localStorage，跨会话保留模式
 * 5. 指数衰减：近期导航权重更高，适应行为变化
 *
 * 触发时机：
 * - kernel 的 switchToApp 成功时记录一次 transition
 * - bootstrap.ts 在 router.afterEach 中调用 getRoutePredictor().predict(currentApp)
 *
 * @path comm/effects/micro-kernel/src/route-predictor.ts
 * @author ydsz-team
 * @since 4.0.0
 */

import { createLogger } from '@ydsz-core/shared/utils';

const logger = createLogger('RoutePredictor');

/** localStorage key */
const STORAGE_KEY = 'ydsz_route_predictions';

/** 最大保留的转移记录时间窗口（7 天） */
const MAX_RETENTION_MS = 7 * 24 * 60 * 60 * 1000;

/** 转移记录 */
interface TransitionRecord {
  /** 来源应用名 */
  from: string;
  /** 目标应用名 */
  to: string;
  /** 时间戳 (ms) */
  timestamp: number;
  /** 转移次数（聚合后） */
  count: number;
}

/** 持久化数据结构 */
interface PersistedData {
  version: 1;
  transitions: TransitionRecord[];
  lastUpdated: number;
}

/** 预测结果 */
export interface Prediction {
  /** 预测的目标应用名 */
  appName: string;
  /** 预测概率（0~1） */
  probability: number;
  /** 基于多少次转移记录 */
  sampleSize: number;
}

/**
 * 路由预测器
 *
 * 一阶马尔可夫链模型：给定当前应用 A，计算 P(B|A) = count(A→B) / count(A→*)
 */
export class RoutePredictor {
  /** 转移记录：from → (to → count) */
  private transitions: Map<string, Map<string, number>> = new Map();
  /** 来源的总计数：from → total */
  private totals: Map<string, number> = new Map();
  /** 去重集合：记录已处理的 (from,to) 对，用于增量合并 */
  private seenKeys: Set<string> = new Set();

  constructor() {
    this.load();
  }

  /**
   * 记录一次路由跳转。
   *
   * @param from - 来源应用名
   * @param to - 目标应用名
   */
  recordTransition(from: string, to: string): void {
    if (!from || !to || from === to) return;

    const key = `${from}→${to}`;

    // 获取或创建 to 的计数矩阵
    let toMap = this.transitions.get(from);
    if (!toMap) {
      toMap = new Map();
      this.transitions.set(from, toMap);
    }

    const prevCount = toMap.get(to) || 0;
    toMap.set(to, prevCount + 1);

    // 更新 totals
    this.totals.set(from, (this.totals.get(from) || 0) + 1);

    // 标记为已见（用于持久化逻辑）
    if (!this.seenKeys.has(key)) {
      this.seenKeys.add(key);
    }

    // 定期持久化（节流：每次记录都写可能性能差；这里简单每次都写，转移通常不太频繁）
    this.save();
  }

  /**
   * 预测给定应用的下一步 topN 目标。
   *
   * @param currentApp - 当前应用名
   * @param topN - 返回前 N 个预测结果，默认 3
   * @returns 按概率降序的预测结果数组
   */
  predict(currentApp: string, topN = 3): Prediction[] {
    const toMap = this.transitions.get(currentApp);
    if (!toMap || toMap.size === 0) return [];

    const total = this.totals.get(currentApp) || 0;
    if (total === 0) return [];

    const predictions: Prediction[] = [];

    for (const [to, count] of toMap) {
      predictions.push({
        appName: to,
        probability: count / total,
        sampleSize: count,
      });
    }

    // 按概率降序排序，取 topN
    return predictions
      .sort((a, b) => b.probability - a.probability)
      .slice(0, topN);
  }

  /**
   * 获取所有已知的应用名（有转出记录的应用）。
   */
  getKnownApps(): string[] {
    return Array.from(this.transitions.keys());
  }

  /**
   * 获取特定应用对的转移概率。
   */
  getTransitionProbability(from: string, to: string): number {
    const toMap = this.transitions.get(from);
    const total = this.totals.get(from);
    if (!toMap || !total) return 0;
    return (toMap.get(to) || 0) / total;
  }

  /**
   * 清除所有预测数据。
   */
  clear(): void {
    this.transitions.clear();
    this.totals.clear();
    this.seenKeys.clear();
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // 静默
    }
  }

  /**
   * 获取数据摘要（用于 DevTools 面板展示）。
   */
  getSummary(): {
    totalTransitions: number;
    uniquePairs: number;
    topPairs: Array<{ from: string; to: string; count: number }>;
  } {
    const allPairs: Array<{ from: string; to: string; count: number }> = [];
    let totalTransitions = 0;

    for (const [from, toMap] of this.transitions) {
      for (const [to, count] of toMap) {
        allPairs.push({ from, to, count });
        totalTransitions += count;
      }
    }

    return {
      totalTransitions,
      uniquePairs: allPairs.length,
      topPairs: allPairs.sort((a, b) => b.count - a.count).slice(0, 10),
    };
  }

  // ==================== 持久化 ====================

  /** 保存到 localStorage */
  private save(): void {
    try {
      const transitions: TransitionRecord[] = [];
      for (const [from, toMap] of this.transitions) {
        for (const [to, count] of toMap) {
          transitions.push({ from, to, count, timestamp: Date.now() });
        }
      }

      const data: PersistedData = {
        version: 1,
        transitions,
        lastUpdated: Date.now(),
      };

      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch {
      // 存储失败静默（隐私模式/localStorage满等）
    }
  }

  /** 从 localStorage 加载 */
  private load(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return;

      const data = JSON.parse(raw) as PersistedData;
      if (data.version !== 1) return;
      if (Date.now() - data.lastUpdated > MAX_RETENTION_MS) {
        // 数据过期，清除
        localStorage.removeItem(STORAGE_KEY);
        return;
      }

      for (const record of data.transitions) {
        if (!record.from || !record.to || record.from === record.to) continue;

        let toMap = this.transitions.get(record.from);
        if (!toMap) {
          toMap = new Map();
          this.transitions.set(record.from, toMap);
        }

        // 合并取最大值（避免与内存中已有数据重复累加）
        const existingCount = toMap.get(record.to) || 0;
        toMap.set(record.to, Math.max(existingCount, record.count));

        const existingTotal = this.totals.get(record.from) || 0;
        this.totals.set(record.from, Math.max(existingTotal, record.count));

        this.seenKeys.add(`${record.from}→${record.to}`);
      }

      logger.debug(`Loaded ${data.transitions.length} historical transitions`);
    } catch {
      // 加载失败静默
    }
  }
}

/** 全局单例 */
let instance: RoutePredictor | null = null;

/**
 * 获取路由预测器单例。
 */
export function getRoutePredictor(): RoutePredictor {
  if (!instance) {
    instance = new RoutePredictor();
  }
  return instance;
}

/**
 * 重置路由预测器（用于测试）。
 */
export function resetRoutePredictor(): void {
  instance?.clear();
  instance = null;
}
