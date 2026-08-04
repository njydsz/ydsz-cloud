/**
 * Speculation Rules API 集成（Chrome 108+）
 *
 * 利用浏览器原生的 <script type="speculationrules"> 声明式预取子应用 ESM 模块，
 * 无需等待 JavaScript 执行即可由浏览器网络层提前拉取。
 *
 * 与 requestIdleCallback 预加载互补：
 * - Speculation Rules：在 HTML 解析阶段即生效，延迟极低
 * - requestIdleCallback：在浏览器空闲时触发，可结合频率数据动态决策
 *
 * @path comm/effects/micro-kernel/src/speculation-rules.ts
 * @author ydsz-team
 * @since 3.7.0
 */

import type { MicroAppEntry } from '@ydsz/micro-runtime';
import type { PrefetchStrategy } from './preload-strategy';
import { createLogger } from '@ydsz-core/shared/utils';

const logger = createLogger('MicroKernel');

/** 已注入的 speculation rules 元素（避免重复注入） */
let injectedScriptElement: HTMLScriptElement | null = null;

/** 当前注入的规则版本（用于去重） */
let injectedVersion = '';

/**
 * 检查浏览器是否支持 Speculation Rules API。
 *
 * 通过检测 document.createElement('script').speculationrules 属性是否存在判断。
 * Chrome 108+、Edge 108+ 支持；Firefox、Safari 不支持（降级到 requestIdleCallback）。
 */
export function isSpeculationRulesSupported(): boolean {
  try {
    const script = document.createElement('script');
    return 'speculationrules' in script;
  } catch {
    return false;
  }
}

/**
 * 构建 Speculation Rules JSON 配置。
 *
 * 为每个子应用生成 prerender/prefetch 规则，使用 eagerness: 'moderate' 表示
 * 鼠标悬停时开始预取（不立即拉取，平衡带宽与速度）。
 *
 * @param apps - 子应用注册表（含 entry URL）
 * @param maxRules - 最大规则数（浏览器上限通常为 10），默认 5
 * @returns 序列化后的 JSON 字符串
 */
export function buildSpeculationRules(apps: MicroAppEntry[], maxRules = 5): string {
  const candidates = apps.slice(0, maxRules);

  const rules = {
    prefetch: [
      {
        source: 'list',
        urls: candidates.map((a) => a.entry || `/ydsz-${a.name}/`),
        where: {
          // 匹配当前域名下的所有子应用路径
          'href_matches': candidates.map((a) => `${a.activeRule}/*`),
          // 仅匹配同站（不跨站预取）
          'same_origin': true,
        },
        // eagerness: moderate = hover 200ms 后触发
        eagerness: 'moderate' as const,
      },
    ],
  };

  return JSON.stringify(rules);
}

/**
 * 注入 Speculation Rules 到 <head>。
 *
 * 已注入相同版本时跳过；需要先移除旧规则再注入新规则。
 *
 * @param apps - 子应用注册表
 * @param maxRules - 最大预取规则数
 * @returns true 表示成功注入；false 表示浏览器不支持或注入失败
 */
export function injectSpeculationRules(apps: MicroAppEntry[], maxRules = 5): boolean {
  if (!isSpeculationRulesSupported()) {
    logger.debug('Speculation Rules API not supported, falling back to requestIdleCallback');
    return false;
  }

  if (apps.length === 0) return false;

  // 使用 entry 列表的 hash 作为版本号（去重）
  const version = apps.map((a) => a.entry || a.name).join('|');
  if (version === injectedVersion && injectedScriptElement) {
    return true; // 已注入且未变化，跳过
  }

  try {
    // 移除旧规则
    if (injectedScriptElement) {
      injectedScriptElement.remove();
      injectedScriptElement = null;
    }

    const script = document.createElement('script');
    script.type = 'speculationrules';
    script.textContent = buildSpeculationRules(apps, maxRules);
    document.head.appendChild(script);

    injectedScriptElement = script;
    injectedVersion = version;

    logger.info(`Speculation Rules injected (${Math.min(apps.length, maxRules)} apps)`);
    return true;
  } catch (err) {
    logger.warn(`Failed to inject Speculation Rules: ${String(err)}`);
    return false;
  }
}

/**
 * 移除已注入的 Speculation Rules。
 *
 * 通常在 HMR 或应用列表变化时调用。
 */
export function removeSpeculationRules(): void {
  try {
    if (injectedScriptElement) {
      injectedScriptElement.remove();
      injectedScriptElement = null;
      injectedVersion = '';
    }
  } catch {
    // 静默
  }
}

/**
 * 联合预加载策略执行器。
 *
 * 当 prefetchStrategy 为 'eager' 或 Speculation Rules 可用时：
 * 1. 优先注入 Speculation Rules（浏览器原生层，延迟最低）
 * 2. 对 Speculation Rules 未覆盖的高频应用走 requestIdleCallback 补充
 *
 * @param apps - 子应用注册表
 * @param prefetchStrategy - 来自 start options 的预加载模式
 * @returns 执行结果：speculationRules / idle / skipped / unsupported
 */
export function applyPrefetchBoost(
  apps: MicroAppEntry[],
  prefetchStrategy: PrefetchStrategy,
): 'speculationRules' | 'idle' | 'skipped' | 'unsupported' {
  if (prefetchStrategy === 'never') {
    return 'skipped';
  }

  // 尝试 Speculation Rules
  if (injectSpeculationRules(apps)) {
    return 'speculationRules';
  }

  // 降级：浏览器不支持时记录
  if (!isSpeculationRulesSupported()) {
    return 'unsupported';
  }

  // 注入失败时回退到调用方已有的 idle 预加载
  return 'idle';
}
