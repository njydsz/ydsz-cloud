/**
 * 全局搜索 composable（v4.0）
 *
 * 为全局搜索面板提供搜索数据源注册、收集、搜索接口。
 * 子应用可通过 registerSearchProvider 注册自己的搜索项。
 *
 * @since 4.0.0
 */

import { ref, computed, readonly } from 'vue';

/** 搜索项 */
export interface SearchItem {
  id: string;
  title: string;
  description?: string;
  icon?: string;
  path?: string;
  appName: string;
  appLabel?: string;
  onClick?: () => void;
  /** 内部字段：高亮标题 */
  highlightedTitle?: string;
}

type SearchProvider = () => SearchItem[];

/** 已注册的搜索提供者 */
const providers = new Map<string, SearchProvider>();

/** 搜索项缓存（定期收集） */
const cachedItems = ref<SearchItem[]>([]);

/** 收集间隔（子应用注册/菜单变化时刷新） */
let collectTimer: ReturnType<typeof setTimeout> | null = null;

/**
 * 注册搜索数据提供者。
 *
 * 子应用应在 mount 时调用，传入返回搜索项数组的函数。
 *
 * @param appName   子应用名
 * @param provider  搜索项生产函数
 * @returns 取消注册函数
 *
 * @example
 * onMounted(() => {
 *   registerSearchProvider('project-web', () => [
 *     { id: 'proj-list', title: '项目列表', appName: 'project-web', path: '/ydsz-proj/opportunities' },
 *   ]);
 * });
 */
export function registerSearchProvider(
  appName: string,
  provider: SearchProvider,
): () => void {
  providers.set(appName, provider);
  scheduleCollect();
  return () => {
    providers.delete(appName);
    scheduleCollect();
  };
}

function scheduleCollect(): void {
  if (collectTimer) return;
  collectTimer = setTimeout(() => {
    collectTimer = null;
    collectItems();
  }, 50);
}

function collectItems(): void {
  const all: SearchItem[] = [];
  for (const [appName, provider] of providers) {
    try {
      const items = provider();
      all.push(...items.map((item) => ({ ...item, appName })));
    } catch (err) {
      console.warn(`[global-search] provider ${appName} failed:`, err);
    }
  }
  cachedItems.value = all;
}

/**
 * 全局搜索 composable —— 面板组件内使用。
 *
 * @example
 * // global-search.vue 内
 * const { items, appNameLabels } = useGlobalSearch();
 */
export function useGlobalSearch() {
  const appNameLabels = computed<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    for (const item of cachedItems.value) {
      if (!map[item.appName]) map[item.appName] = item.appLabel || item.appName;
    }
    return map;
  });

  const refresh = () => collectItems();

  return { items: readonly(cachedItems), appNameLabels, refresh };
}

/**
 * 子应用快捷注册搜索项的工具函数。
 *
 * @example
 * import { useSearchProvider } from '@ydsz/micro-runtime/search';
 * useSearchProvider('project-web', [
 *   { id: 'list', title: '项目列表', path: '/proj/list' },
 *   { id: 'new',  title: '新建项目',  path: '/proj/new' },
 * ]);
 */
export function useSearchProvider(appName: string, items: (() => SearchItem[]) | SearchItem[]): (() => void) {
  if (typeof window === 'undefined') return () => {};

  const provider: SearchProvider = typeof items === 'function' ? items : () => items;
  // 延迟注册 —— 确保挂载后执行
  const id = setTimeout(() => {
    registerSearchProvider(appName, provider);
  }, 100);
  return () => {
    clearTimeout(id);
    providers.delete(appName);
  };
}

/**
 * 动态注册搜索项（子应用运行时调用，例如菜单变化后更新搜索源）。
 */
export function addSearchItems(appName: string, items: SearchItem[]): void {
  const existing = providers.get(appName);
  if (existing) {
    providers.set(appName, () => [...existing(), ...items]);
  } else {
    providers.set(appName, () => items);
  }
  scheduleCollect();
}

export { cachedItems };
