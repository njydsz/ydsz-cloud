/**
 * 子应用点对点通信（请求-响应模型）
 *
 * 在现有 globalState pub/sub 基础上，提供 request/response 式的点对点通信：
 * - 主应用调用 sendToApp(appName, payload, awaitResponse?)
 * - 子应用通过 onAppMessage 注册处理器
 * - 可选 await 对方响应 Promise（基于随机 correlationId 关联）
 *
 * @path comm/effects/micro-kernel/src/message-broker.ts
 * @author ydsz-team
 * @since 3.7.0
 */

import { createLogger } from '@ydsz-core/shared/utils';

const logger = createLogger('MicroKernel');

/** 点对点消息结构 */
export interface MicroMessage<T = unknown> {
  /** 发送方应用名 */
  from: string;
  /** 接收方应用名（或 'main'） */
  to: string;
  /** 业务 action */
  action: string;
  /** 业务数据 */
  payload: T;
  /** 消息唯一 id（自动用于响应关联） */
  correlationId: string;
  /** 是否为响应消息 */
  isResponse?: boolean;
}

/** 消息处理器 */
export type MessageHandler<T = unknown, R = unknown> = (message: MicroMessage<T>) => R | Promise<R>;

/** 待确认的请求 */
interface PendingRequest {
  resolve: (value: unknown) => void;
  reject: (reason: unknown) => void;
  timer: ReturnType<typeof setTimeout>;
}

/** 默认请求超时（ms） */
const REQUEST_TIMEOUT = 10_000;

/** 点对点通信事件名 */
const MESSAGE_EVENT = 'micro-kernel:message';

/** 消息处理器注册表（接收方应用名 → handler） */
const handlers = new Map<string, MessageHandler>();

/** 待确认的请求表 */
const pendingRequests = new Map<string, PendingRequest>();

/**
 * 生成随机 correlationId。
 */
function generateCorrelationId(): string {
  return `msg_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

/**
 * 注册应用的消息处理器。
 *
 * @param appName - 应用名
 * @param handler - 消息处理器，返回值将作为响应发回
 * @returns 取消注册函数
 */
export function registerAppMessageHandler<T = unknown, R = unknown>(
  appName: string,
  handler: MessageHandler<T, R>,
): () => void {
  handlers.set(appName, handler as MessageHandler);
  logger.debug(`Message handler registered for "${appName}"`);
  return () => {
    handlers.delete(appName);
  };
}

/**
 * 发送消息到指定子应用。
 *
 * @param to - 目标应用名
 * @param action - 业务 action
 * @param payload - 业务数据
 * @returns 消息 id（用于调试跟踪）
 */
export function sendMessage(to: string, action: string, payload?: unknown): string {
  const correlationId = generateCorrelationId();
  const message: MicroMessage = {
    from: 'main',
    to,
    action,
    payload,
    correlationId,
  };
  window.dispatchEvent(new CustomEvent(MESSAGE_EVENT, { detail: message }));
  return correlationId;
}

/**
 * 发送请求并等待子应用响应。
 *
 * @param to - 目标应用名
 * @param action - 业务 action
 * @param payload - 业务数据
 * @param timeout - 超时（ms）
 * @returns 响应数据
 */
export function sendRequest<T = unknown, R = unknown>(
  to: string,
  action: string,
  payload?: T,
  timeout = REQUEST_TIMEOUT,
): Promise<R> {
  return new Promise<R>((resolve, reject) => {
    const correlationId = generateCorrelationId();
    const timer = setTimeout(() => {
      pendingRequests.delete(correlationId);
      reject(new Error(`Request timeout: ${to}/${action}`));
    }, timeout);

    pendingRequests.set(correlationId, {
      resolve: resolve as (value: unknown) => void,
      reject,
      timer,
    });

    const message: MicroMessage<T> = {
      from: 'main',
      to,
      action,
      payload,
      correlationId,
    };
    window.dispatchEvent(new CustomEvent(MESSAGE_EVENT, { detail: message }));
  });
}

/**
 * 接收子应用发送的消息/响应。
 *
 * 调用一次即注册事件监听（幂等：多次调用不会重复注册）。
 *
 * @param onRequest - 收到请求时调用
 */
export function startMessageListener(
  onRequest?: (message: MicroMessage) => void,
): () => void {
  const handler = (event: Event) => {
    const message = (event as CustomEvent<MicroMessage>).detail;
    if (!message || typeof message !== 'object') return;

    // 响应消息：关联 pendingRequests
    if (message.isResponse && message.correlationId) {
      const pending = pendingRequests.get(message.correlationId);
      if (pending) {
        clearTimeout(pending.timer);
        pendingRequests.delete(message.correlationId);
        pending.resolve(message.payload);
        return;
      }
    }

    // 请求消息：调用注册的 handler
    const appHandler = handlers.get(message.to);
    if (appHandler) {
      try {
        const result = appHandler(message);
        // 如果是 Promise，await 后发送响应
        if (result instanceof Promise) {
          result.then((response) => {
            sendResponse(message, response);
          }).catch((err) => {
            sendResponse(message, { error: String(err) });
          });
        } else if (result !== undefined) {
          sendResponse(message, result);
        }
      } catch (err) {
        logger.error(`Handler for "${message.to}" failed:`, err);
        sendResponse(message, { error: String(err) });
      }
    } else {
      logger.debug(`No handler for app "${message.to}"`);
    }

    // 外部回调
    onRequest?.(message);
  };

  window.addEventListener(MESSAGE_EVENT, handler);
  return () => {
    window.removeEventListener(MESSAGE_EVENT, handler);
  };
}

/**
 * 发送响应消息。
 */
function sendResponse(originalMessage: MicroMessage, responsePayload: unknown): void {
  const response: MicroMessage = {
    from: originalMessage.to,
    to: originalMessage.from,
    action: originalMessage.action,
    payload: responsePayload,
    correlationId: originalMessage.correlationId,
    isResponse: true,
  };
  window.dispatchEvent(new CustomEvent(MESSAGE_EVENT, { detail: response }));
}

/**
 * 清理所有 pending 请求。
 * 可用于 HMR / 测试场景。
 */
export function clearPendingRequests(): void {
  for (const { reject, timer } of pendingRequests.values()) {
    clearTimeout(timer);
    reject(new Error('Message broker cleared'));
  }
  pendingRequests.clear();
  handlers.clear();
}
