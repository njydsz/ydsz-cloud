/**
 * dict Pinia 状态管理 — 全局数据字典缓存
 *
 * @path comm\stores\src\modules\dict.ts
 * @author ydsz-team
 * @since 1.0.0
 *
 * @remarks
 * 提供按字典类型（typeCode）分组的全局缓存，支持：
 * - 懒加载：首次访问某字典类型时才请求
 * - TTL 过期：默认 10 分钟，过期后重新拉取
 * - 手动失效：字典维护后可调用 invalidate 清理缓存
 * - 多应用共享：所有子应用共用同一份缓存，避免重复请求
 *
 * 加载器通过 {@link setDictLoader} 注入（由应用层提供 requestClient 实现），
 * 保持本包不依赖 @ydsz/request，遵守包边界约束。
 */
import { acceptHMRUpdate, defineStore } from 'pinia';

/** 字典项数据结构（对齐后端 DictItemVO） */
export interface DictItem {
  /** 主键 */
  id: string;
  /** 字典类型编码 */
  typeCode: string;
  /** 字典项编码 */
  itemCode: string;
  /** 字典项文本 */
  itemText: string;
  /** 字典项值 */
  itemValue: string;
  /** 排序 */
  sort: number;
  /** 状态：1 启用 0 禁用 */
  status?: number;
  [key: string]: any;
}

/** 字典加载器函数签名：传入类型编码，返回字典项数组 */
export type DictLoader = (typeCode: string) => Promise<DictItem[]>;

interface DictCacheEntry {
  /** 缓存数据 */
  items: DictItem[];
  /** 过期时间戳 */
  expiresAt: number;
  /** 是否正在加载（防止并发重复请求） */
  loading?: Promise<DictItem[]>;
}

interface DictState {
  /** typeCode → 缓存条目 */
  cache: Record<string, DictCacheEntry>;
  /** 正在加载的字典类型集合 */
  loadingTypes: Set<string>;
  /** 注入的加载器 */
  loader: DictLoader | null;
}

/** 默认 TTL：10 分钟 */
const DEFAULT_TTL = 10 * 60 * 1000;

/**
 * @zh_CN 数据字典缓存 store
 */
export const useDictStore = defineStore('core-dict', {
  state: (): DictState => ({
    cache: {},
    loadingTypes: new Set(),
    loader: null,
  }),

  getters: {
    /**
     * 获取某字典类型的缓存项（未加载返回空数组，不触发请求）
     */
    getItems: (state) => {
      return (typeCode: string): DictItem[] => {
        const entry = state.cache[typeCode];
        if (!entry || entry.expiresAt < Date.now()) {
          return [];
        }
        return entry.items;
      };
    },
  },

  actions: {
    /**
     * 注入字典加载器（应用启动时调用一次）
     *
     * @param loader - 根据 typeCode 返回字典项数组的异步函数
     */
    setDictLoader(loader: DictLoader) {
      this.loader = loader;
    },

    /**
     * 确保某字典类型已加载；未加载或已过期时异步拉取。
     *
     * @param typeCode - 字典类型编码
     * @param force - 强制刷新（忽略缓存与 TTL）
     * @returns 字典项数组
     */
    async ensureLoaded(typeCode: string, force = false): Promise<DictItem[]> {
      const cached = this.cache[typeCode];
      if (!force && cached && cached.expiresAt >= Date.now()) {
        return cached.items;
      }

      // 并发去重：同一字典类型同时只发一次请求
      if (cached?.loading) {
        return cached.loading;
      }

      if (!this.loader) {
        console.warn('[dict-store] 未注入字典加载器，请先调用 setDictLoader()');
        return [];
      }

      const loading = this.loader(typeCode)
        .then((items) => {
          this.cache[typeCode] = {
            items,
            expiresAt: Date.now() + DEFAULT_TTL,
          };
          return items;
        })
        .finally(() => {
          this.loadingTypes.delete(typeCode);
          delete this.cache[typeCode]?.loading;
        });

      // 先占位 loading，防止重复请求
      if (this.cache[typeCode]) {
        this.cache[typeCode].loading = loading;
      } else {
        this.cache[typeCode] = {
          items: [],
          expiresAt: 0,
          loading,
        };
      }
      this.loadingTypes.add(typeCode);

      return loading;
    },

    /**
     * 同步获取字典项；若未加载则异步拉取后返回（await 使用）。
     *
     * @param typeCode - 字典类型编码
     */
    async getDictItems(typeCode: string): Promise<DictItem[]> {
      const cached = this.cache[typeCode];
      if (cached && cached.expiresAt >= Date.now()) {
        return cached.items;
      }
      return this.ensureLoaded(typeCode);
    },

    /**
     * 使某字典类型缓存失效（字典维护后调用）
     */
    invalidate(typeCode?: string) {
      if (typeCode) {
        delete this.cache[typeCode];
      } else {
        this.cache = {};
      }
    },

    /**
     * 清理过期缓存条目
     */
    prune() {
      const now = Date.now();
      for (const key of Object.keys(this.cache)) {
        const entry = this.cache[key];
        if (entry && entry.expiresAt < now && !entry.loading) {
          delete this.cache[key];
        }
      }
    },
  },
});

// 解决热更新问题
const hot = import.meta.hot;
if (hot) {
  hot.accept(acceptHMRUpdate(useDictStore, hot));
}
