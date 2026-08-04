/**
 * realtime 客户端 — WebSocket/SSE 统一实时通信封装
 *
 * @path comm\effects\shared-business\src\realtime\realtime-client.ts
 * @author ydsz-team
 * @since 1.1.0
 *
 * @remarks
 * 提供 WebSocket 连接管理能力：
 * - 自动重连（指数退避 + 抖动，最长 30s）
 * - 心跳保活（30s 间隔，ping/pong）
 * - 离线事件队列（断线期间的消息在重连后按序重放）
 * - 频道订阅（channel）模式，支持多监听器
 * - 跨 Tab 去重由业务层通过 globalState 处理
 */

/** 重连配置 */
export interface RealtimeOptions {
  /** WebSocket 地址，如 wss://host/ws */
  url: string;
  /** 心跳间隔 ms，默认 30000 */
  heartbeatInterval?: number;
  /** 是否自动重连，默认 true */
  autoReconnect?: boolean;
  /** 最大重连次数，默认 10（-1 表示无限） */
  maxReconnectAttempts?: number;
  /** 是否启用离线消息队列，默认 true */
  offlineQueue?: boolean;
}

/** 连接状态 */
export type RealtimeStatus = 'connecting' | 'open' | 'closed' | 'reconnecting';

/** 消息处理器 */
type MessageHandler = (payload: any, channel: string) => void;

/** 频道事件 */
interface ChannelListener {
  channel: string;
  handler: MessageHandler;
}

const HEARTBEAT_INTERVAL = 30_000;
const MAX_RECONNECT_ATTEMPTS = 10;
const MAX_QUEUE_SIZE = 100;

export class RealtimeClient {
  private ws: WebSocket | null = null;
  private url: string;
  private heartbeatInterval: number;
  private autoReconnect: boolean;
  private maxReconnectAttempts: number;
  private offlineQueue: boolean;

  private status: RealtimeStatus = 'closed';
  private listeners: ChannelListener[] = [];
  private reconnectAttempts = 0;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private offlineMessages: any[] = [];
  private manualClosed = false;

  constructor(options: RealtimeOptions) {
    this.url = options.url;
    this.heartbeatInterval = options.heartbeatInterval ?? HEARTBEAT_INTERVAL;
    this.autoReconnect = options.autoReconnect ?? true;
    this.maxReconnectAttempts = options.maxReconnectAttempts ?? MAX_RECONNECT_ATTEMPTS;
    this.offlineQueue = options.offlineQueue ?? true;
  }

  /** 建立连接 */
  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      return;
    }
    this.manualClosed = false;
    this.setStatus('connecting');

    try {
      this.ws = new WebSocket(this.url);
    } catch (error) {
      console.error('[realtime] WebSocket 创建失败', error);
      this.scheduleReconnect();
      return;
    }

    this.ws.onopen = () => {
      this.reconnectAttempts = 0;
      this.setStatus('open');
      this.startHeartbeat();
      // 重连成功后重放离线消息
      this.replayOffline();
    };

    this.ws.onmessage = (event) => {
      this.handleMessage(event.data);
    };

    this.ws.onclose = () => {
      this.stopHeartbeat();
      this.setStatus('closed');
      this.scheduleReconnect();
    };

    this.ws.onerror = () => {
      // onclose 会随后触发，统一走 scheduleReconnect
    };
  }

  /** 关闭连接（手动关闭不再自动重连） */
  close() {
    this.manualClosed = true;
    this.stopHeartbeat();
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
    this.ws = null;
    this.setStatus('closed');
  }

  /**
   * 订阅频道消息
   *
   * @param channel - 频道名
   * @param handler - 消息回调 (payload, channel)
   * @returns 取消订阅函数
   */
  subscribe(channel: string, handler: MessageHandler): () => void {
    const listener: ChannelListener = { channel, handler };
    this.listeners.push(listener);
    return () => {
      const idx = this.listeners.indexOf(listener);
      if (idx >= 0) this.listeners.splice(idx, 1);
    };
  }

  /** 发送消息（支持待连接时缓存） */
  send(data: any): boolean {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(typeof data === 'string' ? data : JSON.stringify(data));
      return true;
    }
    return false;
  }

  /** 当前连接状态 */
  getStatus(): RealtimeStatus {
    return this.status;
  }

  // ===== 内部实现 =====

  private setStatus(status: RealtimeStatus) {
    this.status = status;
  }

  private handleMessage(raw: any) {
    let message: { channel?: string; payload?: any } | null = null;
    try {
      message = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch {
      message = { payload: raw };
    }

    const channel = message?.channel ?? '';
    const payload = message?.payload ?? message;

    // 心跳响应
    if (channel === '__heartbeat__') return;

    const matched = this.listeners.filter((l) => l.channel === channel);
    if (matched.length > 0) {
      matched.forEach((l) => l.handler(payload, channel));
    }
  }

  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      this.send(JSON.stringify({ channel: '__heartbeat__', payload: { ts: Date.now() } }));
    }, this.heartbeatInterval);
  }

  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  private scheduleReconnect() {
    if (this.manualClosed || !this.autoReconnect) return;
    if (this.maxReconnectAttempts >= 0 && this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.warn('[realtime] 重连次数已达上限，停止重连');
      return;
    }

    // 指数退避 + 抖动：2^n * 1s ± 30%，上限 30s
    const base = Math.min(2 ** this.reconnectAttempts * 1000, 30_000);
    const jitter = base * (0.7 + Math.random() * 0.6);
    this.reconnectAttempts += 1;
    this.setStatus('reconnecting');

    this.reconnectTimer = setTimeout(() => {
      this.connect();
    }, jitter);
  }

  private replayOffline() {
    if (!this.offlineQueue || this.offlineMessages.length === 0) return;
    const messages = [...this.offlineMessages];
    this.offlineMessages = [];
    messages.forEach((msg) => this.handleMessage(msg));
  }

  /** 业务侧可调用：写入离线消息（若已连接则直接分发） */
  dispatch(channel: string, payload: any) {
    if (this.status === 'open') {
      this.handleMessage(JSON.stringify({ channel, payload }));
      return;
    }
    if (this.offlineQueue) {
      if (this.offlineMessages.length >= MAX_QUEUE_SIZE) {
        this.offlineMessages.shift();
      }
      this.offlineMessages.push({ channel, payload });
    }
  }
}
