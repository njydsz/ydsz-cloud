/**
 * 功能开关管理器
 *
 * 三层优先级（高 → 低）：
 *   1. 本地覆盖（localStorage / setEnabled）— 仅开发环境或显式开启时生效
 *   2. 远程配置（remoteLoader，如配置中心）— 生产环境的事实来源
 *   3. 构建期环境变量（VITE_FEATURE_<NAME>，大写下划线）
 *   4. 代码默认值（FeatureFlagDef.defaultValue）
 *
 * 状态采用 Vue reactive，组件侧 useFeatureFlag 返回 computed 可响应变更。
 * 同时提供轻量 pub-sub 监听器，供路由守卫、指令等非组件场景使用。
 *
 * @path comm/@core/feature-flags/src/feature-flags.ts
 * @author ydsz-team
 * @since 1.0.0
 */
import type {
  FeatureFlagDef,
  FeatureFlagValue,
  FeatureFlagsChangeListener,
  FeatureFlagsOptions,
} from './types';

import { reactive, readonly } from 'vue';

import { StorageManager } from '@ydsz-core/shared/cache';

/** localStorage 子键（挂在 namespace 之下） */
const STORAGE_KEY = 'feature-flags';
/** 环境变量前缀：VITE_FEATURE_NEW_DASHBOARD → flag 'new-dashboard' */
const ENV_PREFIX = 'VITE_FEATURE_';

class FeatureFlagsManager {
  /** 已注册开关定义（name → def） */
  private defs = new Map<string, FeatureFlagDef>();
  /** 本地覆盖（name → boolean），来源：localStorage / setEnabled */
  private localOverrides = reactive<Record<string, boolean>>({});
  /** 远程配置（name → boolean），来源：remoteLoader */
  private remoteValues: Record<string, boolean> = {};
  /** 解析后的最终状态（对外只读暴露，驱动组件响应） */
  private state = reactive<Record<string, boolean>>({});

  private storage: null | StorageManager = null;
  private remoteLoader: FeatureFlagsOptions['remoteLoader'];
  private allowLocalOverrideInProd: boolean;
  /**
   * 环境变量来源（由应用在 init 时注入 `import.meta.env`）。
   *
   * 库本身不直接读取 import.meta.env —— 因为作为依赖被预打包时
   * import.meta.env 会被静态替换为快照，运行期 stubEnv 无法生效，
   * 也会与宿主应用的 env 实例脱钩。由应用注入保证同源。
   */
  private env: Record<string, unknown> = {};
  private isDev: boolean;
  private initialized = false;
  private listeners = new Set<FeatureFlagsChangeListener>();

  constructor() {
    this.remoteLoader = undefined;
    this.allowLocalOverrideInProd = false;
    // 兜底：未调用 init 前按 dev 处理（开发态默认宽松）。
    // 生产环境必须在 bootstrap 中调用 init({ env: import.meta.env }) 覆盖。
    this.isDev = true;
  }

  /**
   * 注册开关定义（可在应用 bootstrap 前调用，支持增量注册）。
   *
   * 注册后立即触发一次解析，使默认值生效。
   */
  register(defs: FeatureFlagDef[]): void {
    for (const def of defs) {
      this.defs.set(def.name, { allowLocalOverride: true, ...def });
    }
    this.resolve();
  }

  /** 查询已注册开关定义 */
  getDef(name: string): FeatureFlagDef | undefined {
    return this.defs.get(name);
  }

  /**
   * 初始化：注入环境变量源、绑定存储、加载本地覆盖、触发远程加载。
   *
   * 必须在应用 bootstrap 中调用一次，传入 `import.meta.env`：
   *   await initFeatureFlags({ env: import.meta.env, namespace, remoteLoader });
   */
  async init(options: FeatureFlagsOptions = {}): Promise<void> {
    if (this.initialized) {
      return;
    }
    this.initialized = true;

    this.env = options.env ?? {};
    this.isDev =
      this.env.DEV === true || this.env.MODE === 'development';

    const namespace = options.namespace ?? '';
    this.storage = new StorageManager({ prefix: namespace });
    this.remoteLoader = options.remoteLoader;
    this.allowLocalOverrideInProd = options.enableLocalOverrideInProd ?? false;

    // 加载本地覆盖（仅在允许本地覆盖时）
    if (this.canUseLocalOverride()) {
      this.loadLocalOverrides();
    }

    // 触发远程加载（不阻塞 init，失败仅警告）
    if (this.remoteLoader) {
      this.loadRemote().catch(() => {
        /* loadRemote 内部已处理错误 */
      });
    }

    this.resolve();
  }

  /** 是否允许本地覆盖：开发环境始终允许，生产环境按配置 */
  private canUseLocalOverride(): boolean {
    if (this.isDev) return true;
    return this.allowLocalOverrideInProd;
  }

  /**
   * 查询开关是否开启。
   *
   * 未注册的开关返回 false（避免拼写错误导致"默认开启"）。
   */
  isEnabled(name: string): boolean {
    return this.state[name] ?? false;
  }

  /** 获取只读的完整状态快照（响应式，组件 computed 中可追踪） */
  getAll(): Readonly<Record<string, boolean>> {
    return readonly(this.state);
  }

  /**
   * 显式设置本地覆盖并持久化。
   *
   * - 生产环境且未开启 enableLocalOverrideInProd 时调用为 no-op 并警告。
   * - allowLocalOverride=false 的开关同样拒绝（合规保护）。
   */
  setEnabled(name: string, value: boolean): void {
    const def = this.defs.get(name);
    if (!def) {
      console.warn(`[FeatureFlags] Unknown flag "${name}" — ignored`);
      return;
    }
    if (!this.canUseLocalOverride() || def.allowLocalOverride === false) {
      console.warn(
        `[FeatureFlags] Local override for "${name}" is not allowed in current environment`,
      );
      return;
    }
    this.localOverrides[name] = value;
    if (this.canUseLocalOverride()) {
      this.persistLocalOverride(name, value);
    }
    this.resolveSingle(name);
    this.notify(name);
  }

  /** 移除本地覆盖（回退到远程 / 默认值） */
  resetFlag(name: string): void {
    if (!(name in this.localOverrides)) return;
    delete this.localOverrides[name];
    this.storage?.removeItem(`${STORAGE_KEY}:${name}`);
    this.resolveSingle(name);
    this.notify(name);
  }

  /**
   * 异步加载远程开关配置。
   *
   * 失败时仅打印警告，不抛错，保留既有状态（降级而非阻断）。
   */
  async loadRemote(): Promise<void> {
    if (!this.remoteLoader) return;
    try {
      const raw = await this.remoteLoader();
      this.remoteValues = normalizeValues(raw);
      this.resolve();
      // 远程加载后批量通知（粗粒度，避免风暴）
      for (const name of Object.keys(this.remoteValues)) {
        this.notify(name);
      }
    } catch (err) {
      console.warn('[FeatureFlags] Remote loader failed:', err);
    }
  }

  /** 订阅变更（返回取消订阅函数） */
  onChange(listener: FeatureFlagsChangeListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  /** 清空所有注册与状态（测试 / HMR 用） */
  reset(): void {
    this.defs.clear();
    for (const key of Object.keys(this.localOverrides)) {
      delete this.localOverrides[key];
    }
    this.remoteValues = {};
    for (const key of Object.keys(this.state)) {
      delete this.state[key];
    }
    this.listeners.clear();
    this.env = {};
    this.isDev = true;
    this.initialized = false;
  }

  // ==================== 内部 ====================

  /** 解析单个开关的最终值（按优先级） */
  private resolveOne(name: string): boolean {
    const def = this.defs.get(name);
    if (!def) return false;

    // 1. 本地覆盖（仅当允许）
    if (name in this.localOverrides) {
      return this.localOverrides[name];
    }
    // 2. 远程配置
    if (name in this.remoteValues) {
      return this.remoteValues[name];
    }
    // 3. 构建期环境变量
    const envKey = `${ENV_PREFIX}${name.toUpperCase().replace(/-/g, '_')}`;
    const envVal = this.readEnv(envKey);
    if (envVal !== undefined) return envVal;
    // 4. 代码默认值
    return def.defaultValue;
  }

  /** 从注入的 env 源读取构建期环境变量 */
  private readEnv(key: string): boolean | undefined {
    const raw = this.env[key];
    if (raw === undefined || raw === null || raw === '') return undefined;
    if (raw === true || raw === 'true' || raw === 1 || raw === '1') return true;
    if (raw === false || raw === 'false' || raw === 0 || raw === '0') {
      return false;
    }
    return undefined;
  }

  /** 全量解析（注册 / 远程加载后调用） */
  private resolve(): void {
    for (const name of this.defs.keys()) {
      this.state[name] = this.resolveOne(name);
    }
  }

  /** 单个解析（仅该开关变更时，避免全量重算） */
  private resolveSingle(name: string): void {
    this.state[name] = this.resolveOne(name);
  }

  private notify(name: string): void {
    const value = this.state[name];
    for (const listener of this.listeners) {
      try {
        listener(name, value);
      } catch {
        /* 监听器异常不影响后续 */
      }
    }
  }

  // ==================== 持久化 ====================

  private loadLocalOverrides(): void {
    const stored = this.storage?.getItem<Record<string, FeatureFlagValue>>(
      STORAGE_KEY,
    );
    if (!stored) return;
    const normalized = normalizeValues(stored);
    for (const [name, value] of Object.entries(normalized)) {
      const def = this.defs.get(name);
      // 仅恢复已注册且允许本地覆盖的开关
      if (def && def.allowLocalOverride !== false) {
        this.localOverrides[name] = value;
      }
    }
  }

  private persistLocalOverride(name: string, value: boolean): void {
    // 增量持久化：读取现有 map → 更新 → 写回
    const stored =
      this.storage?.getItem<Record<string, FeatureFlagValue>>(STORAGE_KEY) ??
      {};
    stored[name] = value;
    this.storage?.setItem(STORAGE_KEY, stored);
  }
}

/** 将任意值映射归一化为 boolean map */
function normalizeValues(
  raw: Record<string, FeatureFlagValue>,
): Record<string, boolean> {
  const out: Record<string, boolean> = {};
  for (const [key, value] of Object.entries(raw)) {
    if (value === true || value === 'on') {
      out[key] = true;
    } else if (value === false || value === 'off') {
      out[key] = false;
    }
  }
  return out;
}

const featureFlagsManager = new FeatureFlagsManager();

export { FeatureFlagsManager, featureFlagsManager };
