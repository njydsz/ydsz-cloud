/**
 * 跨标签页状态同步管理器
 *
 * 基于 BroadcastChannel API（优先）+ storage event 兜底，实现：
 *   - 同源多标签页之间的状态广播
 *   - 主题/语言/偏好变更即时同步
 *   - 登出/会话失效跨标签页联动
 *   - 功能开关本地覆盖同步（dev 环境）
 *
 * 设计要点：
 *   1. 单一通道名 `channelName`，所有消息走同一 channel；通过 `type` 字段区分主题。
 *      避免 BroadcastChannel 数量膨胀（浏览器对通道数量有限制）。
 *   2. 消息携带 `origin` 标识发送者实例 ID，接收端默认跳过本实例发出的消息，
 *      避免自身回响（除非显式 echo=true）。
 *   3. 兜底走 localStorage 时，复用 storage event 的原生特性（写入即触发其它标签页）。
 *   4. 消息负载 JSON 序列化，结构变更通过 `version` 兼容性检查。
 *
 * @path comm/@core/base/shared/src/cache/broadcast-channel.ts
 * @author ydsz-team
 * @since 1.0.0
 */

/** 广播消息载荷 */
export interface BroadcastMessage<T = unknown> {
  /** 消息类型（同一 channel 下区分主题，例如 'logout' / 'theme' / 'locale'） */
  type: string;
  /** 消息负载 */
  payload: T;
  /** 发送者实例 ID（接收端默认跳过自身） */
  origin: string;
  /** 协议版本，跨版本不兼容时降级为警告并忽略 */
  v?: number;
}

/** 监听器签名 */
export type BroadcastListener<T = unknown> = (
  message: BroadcastMessage<T>,
) => void;

/** BroadcastChannelManager 配置 */
export interface BroadcastChannelOptions {
  /** 通道名称（同源多标签页共享） */
  channelName: string;
  /**
   * localStorage 兜底使用的 key（默认 `${channelName}:broadcast`）。
   * 仅在 BroadcastChannel API 不可用时启用。
   */
  storageKey?: string;
  /** 当前实例 ID（默认每次 new 生成，便于跨实例去重） */
  instanceId?: string;
  /** 协议版本，结构变更时递增 */
  version?: number;
  /**
   * 是否接收自身发出的消息（默认 false）。
   * 同一标签页内订阅同一 type 时，默认不触发，避免回响。
   */
  echo?: boolean;
}

/** 协议默认版本 */
const DEFAULT_VERSION = 1;

/**
 * 跨标签页广播管理器。
 *
 * 优先使用 BroadcastChannel API（性能更好、语义清晰）；
 * 在不支持的环境（旧版 Safari / 部分内嵌 WebView）自动降级为 localStorage 事件。
 */
export class BroadcastChannelManager<T = unknown> {
  private channel: BroadcastChannel | null = null;
  private listeners = new Map<string, Set<BroadcastListener<T>>>();
  private options: Required<Omit<BroadcastChannelOptions, 'instanceId'>> & {
    instanceId: string;
  };
  /** 兜底 storage 是否已绑定 listener */
  private storageBound = false;

  constructor(options: BroadcastChannelOptions) {
    this.options = {
      channelName: options.channelName,
      storageKey: options.storageKey ?? `${options.channelName}:broadcast`,
      instanceId: options.instanceId ?? generateInstanceId(),
      version: options.version ?? DEFAULT_VERSION,
      echo: options.echo ?? false,
    };

    if (typeof BroadcastChannel !== 'undefined') {
      this.channel = new BroadcastChannel(this.options.channelName);
      this.channel.addEventListener('message', this.handleMessage);
    } else if (typeof window !== 'undefined' && window.addEventListener) {
      // 降级：storage 事件仅在新值与旧值不同时触发，且只通知其它标签页
      window.addEventListener('storage', this.handleStorageEvent);
      this.storageBound = true;
    }
  }

  /**
   * 发布消息到所有标签页。
   *
   * @param type 消息类型
   * @param payload 消息负载（将被 JSON 序列化）
   * @param options.echo 是否触发本实例监听器（覆盖构造期 echo 设置）
   */
  postMessage<P = T>(
    type: string,
    payload: P,
    options?: { echo?: boolean },
  ): void {
    const message: BroadcastMessage<P> = {
      type,
      payload,
      origin: this.options.instanceId,
      v: this.options.version,
    };

    // 序列化（提前失败比静默丢弃好）
    let serialized: string;
    try {
      serialized = JSON.stringify(message);
    } catch (error) {
      console.error(
        `[BroadcastChannel] Failed to serialize message "${type}":`,
        error,
      );
      return;
    }

    // 优先走原生 BroadcastChannel
    if (this.channel) {
      try {
        this.channel.postMessage(message);
      } catch (error) {
        console.error(
          `[BroadcastChannel] Failed to post message "${type}":`,
          error,
        );
      }
    } else if (typeof localStorage !== 'undefined') {
      // 兜底：写入 localStorage 触发其它标签页的 storage 事件
      try {
        localStorage.setItem(this.options.storageKey, serialized);
      } catch (error) {
        console.error(
          `[BroadcastChannel] Failed to write storage fallback for "${type}":`,
          error,
        );
      }
    }

    // 本实例回响（默认关闭）。
    // 直接派发到本地监听器，绕过 origin 检查（echo 是显式请求本实例触发）。
    const echo = options?.echo ?? this.options.echo;
    if (echo) {
      this.dispatchToLocalListeners(message as BroadcastMessage<T>);
    }
  }

  /**
   * 订阅指定 type 的消息。
   *
   * @returns 取消订阅函数
   */
  on(type: string, listener: BroadcastListener<T>): () => void {
    let set = this.listeners.get(type);
    if (!set) {
      set = new Set();
      this.listeners.set(type, set);
    }
    set.add(listener);

    return () => {
      set?.delete(listener);
      if (set && set.size === 0) {
        this.listeners.delete(type);
      }
    };
  }

  /** 一次性订阅：收到首条匹配消息后自动退订 */
  once(type: string, listener: BroadcastListener<T>): () => void {
    const off = this.on(type, (message) => {
      off();
      listener(message);
    });
    return off;
  }

  /** 关闭通道并清理所有监听器（组件卸载时调用） */
  close(): void {
    if (this.channel) {
      this.channel.removeEventListener('message', this.handleMessage);
      this.channel.close();
      this.channel = null;
    }
    if (this.storageBound) {
      window.removeEventListener('storage', this.handleStorageEvent);
      this.storageBound = false;
    }
    this.listeners.clear();
  }

  // ==================== 内部 ====================

  private handleMessage = (event: MessageEvent): void => {
    const message = event.data as BroadcastMessage<T>;
    if (!message || typeof message !== 'object') return;
    this.dispatchToListeners(message);
  };

  private handleStorageEvent = (event: StorageEvent): void => {
    if (event.key !== this.options.storageKey || !event.newValue) return;
    try {
      const message = JSON.parse(event.newValue) as BroadcastMessage<T>;
      this.dispatchToListeners(message);
    } catch (error) {
      console.warn('[BroadcastChannel] Failed to parse storage event:', error);
    }
  };

  /**
   * 派发到本实例监听器（带版本检查与 origin 过滤）。
   *
   * 用于接收来自其它标签页的消息：
   *   - 版本不匹配 → 丢弃并告警
   *   - origin === 本实例 ID → 默认跳过（避免回响），echo=true 时由 postMessage 直接调用 dispatchToLocalListeners
   */
  private dispatchToListeners(message: BroadcastMessage<T>): void {
    // 版本兼容性检查（主版本不一致时丢弃，避免结构错乱）
    if (message.v !== undefined && message.v !== this.options.version) {
      console.warn(
        `[BroadcastChannel] Message version mismatch: got ${message.v}, expected ${this.options.version}; dropped`,
      );
      return;
    }

    // 默认跳过本实例发出的消息，避免回响
    if (!this.options.echo && message.origin === this.options.instanceId) {
      return;
    }

    this.dispatchToLocalListeners(message);
  }

  /**
   * 直接派发到本实例监听器（无 origin 过滤，仅做版本检查）。
   *
   * 由 postMessage 的 echo=true 路径调用，或由 dispatchToListeners 在通过 origin 检查后调用。
   */
  private dispatchToLocalListeners(message: BroadcastMessage<T>): void {
    // 版本兼容性检查（主版本不一致时丢弃）
    if (message.v !== undefined && message.v !== this.options.version) {
      console.warn(
        `[BroadcastChannel] Message version mismatch: got ${message.v}, expected ${this.options.version}; dropped`,
      );
      return;
    }

    const set = this.listeners.get(message.type);
    if (!set) return;

    // 拷贝后遍历，防止监听器在回调中订阅/退订导致迭代异常
    const snapshot = Array.from(set);
    for (const listener of snapshot) {
      try {
        listener(message);
      } catch (error) {
        console.error(
          `[BroadcastChannel] Listener error on "${message.type}":`,
          error,
        );
      }
    }
  }
}

/** 生成短实例 ID（用于跨标签页去重） */
function generateInstanceId(): string {
  // 优先用 crypto.randomUUID（现代浏览器与 Node 19+ 均支持）
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // 回退：时间戳 + 随机数
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * 全局通道注册表，避免同名 channel 被重复创建（多模块共享同一通道时）。
 *
 * 使用方式：
 *   const channel = getSharedBroadcastChannel('ydsz-app');
 *   channel.on('logout', ...);
 */
const sharedChannels = new Map<string, BroadcastChannelManager>();

/**
 * 获取共享广播通道（同 channelName 全局唯一）。
 *
 * 多个模块订阅同一通道时复用同一实例，避免创建多个 BroadcastChannel。
 * 引用计数归零后调用 `releaseSharedBroadcastChannel(name)` 清理。
 */
export function getSharedBroadcastChannel<T = unknown>(
  channelName: string,
  options?: Omit<BroadcastChannelOptions, 'channelName'>,
): BroadcastChannelManager<T> {
  let instance = sharedChannels.get(channelName) as
    | BroadcastChannelManager<T>
    | undefined;
  if (!instance) {
    instance = new BroadcastChannelManager<T>({
      channelName,
      ...options,
    });
    sharedChannels.set(channelName, instance as BroadcastChannelManager);
  }
  return instance;
}

/** 释放共享通道（仅在确信无其他订阅者时调用） */
export function releaseSharedBroadcastChannel(channelName: string): void {
  const instance = sharedChannels.get(channelName);
  if (instance) {
    instance.close();
    sharedChannels.delete(channelName);
  }
}

/**
 * 检测当前环境是否原生支持 BroadcastChannel API。
 *
 * 不支持时自动降级到 localStorage 事件，调用方无需关心。
 */
export function isBroadcastChannelSupported(): boolean {
  return typeof BroadcastChannel !== 'undefined';
}
